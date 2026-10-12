package com.example.huzz.mesh

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.huzz.geohash.ChannelID
import com.example.huzz.geohash.LocationChannelManager
import com.example.huzz.model.EmergencyStatus
import com.example.huzz.model.SosPayload
import com.example.huzz.nostr.NostrEvent
import com.example.huzz.nostr.NostrIdentityBridge
import com.example.huzz.nostr.NostrRelayManager
import com.example.huzz.util.toHexString
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections
import java.util.LinkedHashMap

data class ActiveSosAlert(
    val senderPeerID: String,
    val senderNickname: String,
    val payload: SosPayload,
    val receivedAtMs: Long = System.currentTimeMillis()
)

/**
 * Coordinates Emergency SOS Beacon lifecycle:
 * - 3-second hold sender trigger & periodic re-beaconing (60-120s)
 * - Multi-modal escalation (USAGE_ALARM audio + Morse code vibration ... --- ...)
 * - Anti-replay window & duplicate echo suppression
 * - Authenticated cancellation
 * - Mesh-to-Nostr gateway promotion (#sos)
 */
class SosManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SosManager"
        private const val ANTI_REPLAY_MAX_AGE_MS = 5 * 60 * 1000L // 5 minutes
        private const val ANTI_REPLAY_FUTURE_TOLERANCE_MS = 60 * 1000L // 1 minute
        private const val RE_BEACON_INTERVAL_MS = 60_000L // 60 seconds

        // Morse code pattern for SOS: ... (3 dots) --- (3 dashes) ... (3 dots)
        // [delay, dot, gap, dot, gap, dot, letterGap, dash, gap, dash, gap, dash, letterGap, dot, gap, dot, gap, dot]
        val MORSE_SOS_PATTERN = longArrayOf(
            0,
            150, 150, 150, 150, 150, 400,
            450, 150, 450, 150, 450, 400,
            150, 150, 150, 150, 150
        )

        @Volatile
        private var INSTANCE: SosManager? = null

        fun getInstance(context: Context): SosManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SosManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var reBeaconJob: Job? = null
    private var currentRingtone: Ringtone? = null

    // Track active alerts from remote peers
    private val _activeRemoteAlerts = MutableStateFlow<Map<String, ActiveSosAlert>>(emptyMap())
    val activeRemoteAlerts: StateFlow<Map<String, ActiveSosAlert>> = _activeRemoteAlerts.asStateFlow()

    // Track local user's active SOS beacon
    private val _myActiveSos = MutableStateFlow<SosPayload?>(null)
    val myActiveSos: StateFlow<SosPayload?> = _myActiveSos.asStateFlow()

    // Combined top alert to show in the persistent banner
    private val _primaryAlert = MutableStateFlow<ActiveSosAlert?>(null)
    val primaryAlert: StateFlow<ActiveSosAlert?> = _primaryAlert.asStateFlow()

    // LRU seen-digest filter to prevent broadcast storms / duplicate echoes
    private val seenAlerts = Collections.synchronizedMap(
        object : LinkedHashMap<String, Long>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
                return size > 256
            }
        }
    )

    var onLocalMessageCallback: ((com.example.huzz.model.HuzzMessage) -> Unit)? = null

    private var meshServiceSupplier: (() -> MeshService?)? = {
        try {
            com.example.huzz.service.MeshServiceHolder.getUnifiedOrCreate(context)
        } catch (_: Exception) {
            null
        }
    }

    fun setMeshServiceSupplier(supplier: () -> MeshService?) {
        meshServiceSupplier = supplier
    }

    /**
     * Read current device battery percentage.
     */
    fun getBatteryPercentage(): Int {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        } catch (_: Exception) {
            -1
        }
    }

    /**
     * Read current location geohash.
     */
    fun getCurrentGeohash(): String? {
        return try {
            val locMgr = LocationChannelManager.getInstance(context)
            val sel = locMgr.selectedChannel.value
            if (sel is ChannelID.Location) {
                sel.channel.geohash
            } else {
                locMgr.availableChannels.value.firstOrNull()?.geohash
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Initiate local Emergency SOS beacon broadcast.
     */
    fun broadcastEmergencySos(
        status: EmergencyStatus,
        note: String
    ) {
        val battery = getBatteryPercentage()
        val geohash = getCurrentGeohash()
        val payload = SosPayload(
            status = status,
            batteryPct = battery,
            geohash = geohash,
            note = note,
            timestampMs = System.currentTimeMillis()
        )

        _myActiveSos.value = payload
        updatePrimaryAlert()

        // Post local timeline message
        try {
            val localMsg = com.example.huzz.model.HuzzMessage(
                id = java.util.UUID.randomUUID().toString().uppercase(),
                sender = "You",
                content = "🚨 [EMERGENCY SOS: ${payload.status.displayName.uppercase()}]\n" +
                    "🔋 Battery: ${payload.batteryPct}%\n" +
                    (if (!payload.geohash.isNullOrBlank()) "📍 Geohash: ${payload.geohash}\n" else "") +
                    "📝 Note: ${payload.note}",
                type = com.example.huzz.model.HuzzMessageType.Message,
                senderPeerID = meshServiceSupplier?.invoke()?.myPeerID ?: "self",
                timestamp = java.util.Date(payload.timestampMs)
            )
            onLocalMessageCallback?.invoke(localMsg)
        } catch (_: Exception) {}

        // Send out initial broadcast
        sendBeaconPacket(payload)

        // Start autonomous background re-beaconing every 60 seconds (FR-SOS-08)
        reBeaconJob?.cancel()
        reBeaconJob = scope.launch(Dispatchers.IO) {
            while (isActive && _myActiveSos.value != null) {
                delay(RE_BEACON_INTERVAL_MS)
                val current = _myActiveSos.value ?: break
                Log.i(TAG, "📡 Autonomous background re-beaconing SOS...")
                sendBeaconPacket(current)
            }
        }

        // Bridge to Nostr if connected
        bridgeSosToNostr(payload, meshServiceSupplier?.invoke()?.myPeerID ?: "local")
    }

    /**
     * Authenticated cancellation of local Emergency SOS beacon.
     */
    fun cancelEmergencySos(note: String = "Safe") {
        reBeaconJob?.cancel()
        reBeaconJob = null

        val current = _myActiveSos.value
        val cancelPayload = SosPayload(
            status = EmergencyStatus.CANCELLED,
            batteryPct = getBatteryPercentage(),
            geohash = getCurrentGeohash(),
            note = note,
            timestampMs = System.currentTimeMillis()
        )

        _myActiveSos.value = null
        updatePrimaryAlert()
        stopAlarm()
        try {
            com.example.huzz.ui.NotificationManager(
                context,
                androidx.core.app.NotificationManagerCompat.from(context)
            ).cancelSosNotification()
        } catch (_: Exception) {}

        // Post local timeline cancellation message
        try {
            val cancelMsg = com.example.huzz.model.HuzzMessage(
                id = java.util.UUID.randomUUID().toString().uppercase(),
                sender = "You",
                content = "🕊️ [SOS CANCELLED] You marked yourself safe. Note: $note",
                type = com.example.huzz.model.HuzzMessageType.Message,
                senderPeerID = meshServiceSupplier?.invoke()?.myPeerID ?: "self",
                timestamp = java.util.Date(cancelPayload.timestampMs)
            )
            onLocalMessageCallback?.invoke(cancelMsg)
        } catch (_: Exception) {}

        // Send signed cancellation packet
        sendCancelPacket(cancelPayload)

        Log.i(TAG, "✅ Emergency SOS cancelled by user")
    }

    private fun sendBeaconPacket(payload: SosPayload) {
        try {
            val mesh = meshServiceSupplier?.invoke() ?: return
            mesh.sendSosBeacon(payload)
            Log.i(TAG, "🚨 Broadcasted SOS beacon: ${payload.status} (${payload.batteryPct}%)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to broadcast SOS beacon: ${e.message}", e)
        }
    }

    private fun sendCancelPacket(payload: SosPayload) {
        try {
            val mesh = meshServiceSupplier?.invoke() ?: return
            mesh.sendSosCancel(payload)
            Log.i(TAG, "🕊️ Broadcasted SOS cancel packet")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to broadcast SOS cancel: ${e.message}", e)
        }
    }

    /**
     * Process received SOS beacon from mesh.
     */
    fun onReceivedSosBeacon(
        senderPeerID: String,
        senderNickname: String,
        payload: SosPayload
    ) {
        // Enforce anti-replay window (FR-SOS-06)
        val now = System.currentTimeMillis()
        val age = now - payload.timestampMs
        if (age > ANTI_REPLAY_MAX_AGE_MS || age < -ANTI_REPLAY_FUTURE_TOLERANCE_MS) {
            Log.w(TAG, "Dropping SOS beacon from ${senderPeerID.take(8)} outside anti-replay window (age: ${age}ms)")
            return
        }

        // Drop duplicate echoes (FR-SOS-06)
        val dedupeKey = "${senderPeerID}_${payload.timestampMs}"
        synchronized(seenAlerts) {
            if (seenAlerts.containsKey(dedupeKey)) {
                Log.d(TAG, "Duplicate SOS echo from ${senderPeerID.take(8)} dropped")
                return
            }
            seenAlerts[dedupeKey] = now
        }

        // If payload status is CANCELLED, handle cancellation
        if (payload.status == EmergencyStatus.CANCELLED) {
            onReceivedSosCancel(senderPeerID, payload)
            return
        }

        Log.i(TAG, "🚨 Processing incoming EMERGENCY SOS from $senderNickname (${payload.status})")

        val alert = ActiveSosAlert(
            senderPeerID = senderPeerID,
            senderNickname = senderNickname,
            payload = payload,
            receivedAtMs = now
        )

        val updated = _activeRemoteAlerts.value.toMutableMap()
        updated[senderPeerID] = alert
        _activeRemoteAlerts.value = updated
        updatePrimaryAlert()

        // Multi-modal escalation (FR-SOS-04)
        triggerMultiModalEscalation(alert)
        try {
            com.example.huzz.ui.NotificationManager(
                context,
                androidx.core.app.NotificationManagerCompat.from(context)
            ).showSosNotification(
                senderPeerID = senderPeerID,
                senderNickname = senderNickname,
                status = payload.status.displayName,
                batteryPct = payload.batteryPct,
                geohash = payload.geohash,
                note = payload.note
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post SOS notification: ${e.message}")
        }

        // Gateway promotion to Nostr if internet is available (FR-SOS-07)
        bridgeSosToNostr(payload, senderPeerID)
    }

    /**
     * Process received SOS cancellation from mesh.
     */
    fun onReceivedSosCancel(senderPeerID: String, payload: SosPayload) {
        Log.i(TAG, "🕊️ Received SOS cancellation from ${senderPeerID.take(8)}")
        val updated = _activeRemoteAlerts.value.toMutableMap()
        updated.remove(senderPeerID)
        _activeRemoteAlerts.value = updated
        updatePrimaryAlert()

        if (updated.isEmpty() && _myActiveSos.value == null) {
            stopAlarm()
            try {
                com.example.huzz.ui.NotificationManager(
                    context,
                    androidx.core.app.NotificationManagerCompat.from(context)
                ).cancelSosNotification()
            } catch (_: Exception) {}
        }
    }

    /**
     * Dismiss an alert from view locally.
     */
    fun dismissAlert(senderPeerID: String) {
        val updated = _activeRemoteAlerts.value.toMutableMap()
        updated.remove(senderPeerID)
        _activeRemoteAlerts.value = updated
        updatePrimaryAlert()
        stopAlarm()
        if (updated.isEmpty() && _myActiveSos.value == null) {
            try {
                com.example.huzz.ui.NotificationManager(
                    context,
                    androidx.core.app.NotificationManagerCompat.from(context)
                ).cancelSosNotification()
            } catch (_: Exception) {}
        }
    }

    private fun updatePrimaryAlert() {
        val local = _myActiveSos.value
        if (local != null) {
            _primaryAlert.value = ActiveSosAlert(
                senderPeerID = meshServiceSupplier?.invoke()?.myPeerID ?: "self",
                senderNickname = "You",
                payload = local,
                receivedAtMs = local.timestampMs
            )
        } else {
            _primaryAlert.value = _activeRemoteAlerts.value.values.maxByOrNull { it.receivedAtMs }
        }
    }

    /**
     * Multi-modal escalation:
     * - Bypasses silent mode with USAGE_ALARM tone
     * - Vibrates with Morse code (... --- ...)
     */
    private fun triggerMultiModalEscalation(alert: ActiveSosAlert) {
        scope.launch(Dispatchers.Main) {
            try {
                // 1. Morse code vibration (... --- ...)
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vm?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(MORSE_SOS_PATTERN, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(MORSE_SOS_PATTERN, -1)
                }

                // 2. Emergency alarm tone bypassing silent mode
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                currentRingtone?.stop()
                val ringtone = RingtoneManager.getRingtone(context, alarmUri)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ringtone?.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                }
                ringtone?.play()
                currentRingtone = ringtone

                // Auto silence tone after 6 seconds to avoid endless noise
                delay(6000L)
                if (currentRingtone == ringtone) {
                    ringtone?.stop()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Multi-modal escalation error: ${e.message}")
            }
        }
    }

    fun stopAlarm() {
        try {
            currentRingtone?.stop()
            currentRingtone = null
        } catch (_: Exception) {}
    }

    /**
     * Bridge SOS packet to connected Nostr relays tagged with ["t", "sos"].
     */
    private fun bridgeSosToNostr(payload: SosPayload, senderPeerID: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val identity = NostrIdentityBridge.getCurrentNostrIdentity(context) ?: return@launch
                val tags = mutableListOf(
                    listOf("t", "sos"),
                    listOf("t", "emergency"),
                    listOf("status", payload.status.name),
                    listOf("p", senderPeerID)
                )
                if (!payload.geohash.isNullOrBlank()) {
                    tags.add(listOf("g", payload.geohash))
                }

                val content = "🚨 [EMERGENCY SOS: ${payload.status.displayName}]\n" +
                    "Battery: ${payload.batteryPct}%\n" +
                    (if (!payload.geohash.isNullOrBlank()) "Location (geohash): ${payload.geohash}\n" else "") +
                    "Note: ${payload.note}\n" +
                    "Sender ID: $senderPeerID"

                val event = NostrEvent.createTextNote(
                    content = content,
                    publicKeyHex = identity.publicKeyHex,
                    privateKeyHex = identity.privateKeyHex,
                    tags = tags
                )
                NostrRelayManager.shared.sendEvent(event)
                Log.i(TAG, "🌐 Successfully bridged SOS alert to Nostr relays (#sos)")
            } catch (e: Exception) {
                Log.d(TAG, "Nostr SOS bridge skipped: ${e.message}")
            }
        }
    }
}
