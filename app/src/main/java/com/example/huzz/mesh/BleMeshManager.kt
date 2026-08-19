package com.example.huzz.mesh

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Central orchestrator for the BLE mesh network.
 * Coordinates the GATT server, GATT client, peer manager, and packet processor.
 * Handles sending/receiving messages and relaying packets through the mesh.
 *
 * Adapted from BitChat's BluetoothMeshService + MeshCore pattern.
 */
class BleMeshManager(private val context: Context) {

    companion object {
        private const val TAG = "BleMeshManager"
    }

    // Coroutine scope for mesh operations
    private val meshScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Generate a unique peer ID from a random UUID (persistent per install would be better,
    // but for Phase 1 this suffices)
    val myPeerID: String = generatePeerID()

    // Sub-components
    private val peerManager = PeerManager(meshScope)
    private val packetProcessor = PacketProcessor(myPeerID)

    private var gattServer: BleGattServer? = null
    private var gattClient: BleGattClient? = null

    // Nickname for this device
    private var myNickname: String = "User"

    // --- Public State Flows ---

    enum class MeshStatus {
        Stopped, Starting, Running
    }

    private val _status = MutableStateFlow(MeshStatus.Stopped)
    val status: StateFlow<MeshStatus> = _status.asStateFlow()

    val peerList: StateFlow<List<MeshPeer>> get() = peerManager.peerList
    val activePeerCount: StateFlow<Int> get() = peerManager.activePeerCount

    data class ChatMessage(
        val id: String = UUID.randomUUID().toString(),
        val sender: String,
        val senderPeerID: String,
        val content: String,
        val timestamp: Long = System.currentTimeMillis(),
        val isFromMe: Boolean = false,
        val isRelayed: Boolean = false
    )

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // --- Public API ---

    /**
     * Start the mesh network. Begins advertising and scanning simultaneously.
     */
    fun start(nickname: String) {
        if (_status.value != MeshStatus.Stopped) return

        myNickname = nickname
        _status.value = MeshStatus.Starting

        Log.i(TAG, "Starting mesh as '$nickname' with peer ID: $myPeerID")

        // Start GATT Server (advertise + accept connections)
        gattServer = BleGattServer(
            context = context,
            scope = meshScope,
            myPeerID = myPeerID,
            onPacketReceived = { packet, device -> handleIncomingPacket(packet, device) },
            onDeviceConnected = { device -> handleDeviceConnected(device, null) },
            onDeviceDisconnected = { device -> handleDeviceDisconnected(device) }
        )
        gattServer?.start()

        // Start GATT Client (scan + connect + send)
        gattClient = BleGattClient(
            context = context,
            scope = meshScope,
            myPeerID = myPeerID,
            onPeerDiscovered = { address, peerID, rssi -> handlePeerDiscovered(address, peerID, rssi) },
            onPacketReceived = { packet, device -> handleIncomingPacket(packet, device) },
            onDeviceConnected = { device, peerID -> handleDeviceConnected(device, peerID) },
            onDeviceDisconnected = { device -> handleDeviceDisconnected(device) }
        )
        gattClient?.start()

        // Start peer cleanup
        peerManager.startCleanup()

        // Start periodic seen-cache cleanup
        meshScope.launch {
            while (isActive) {
                delay(MeshConstants.SEEN_CACHE_EXPIRY_MS)
                packetProcessor.cleanupSeenCache()
            }
        }

        // Send initial announcement after a short delay
        meshScope.launch {
            delay(1000)
            sendAnnouncement()
        }

        _status.value = MeshStatus.Running
        Log.i(TAG, "Mesh network started")
    }

    /**
     * Stop the mesh network.
     */
    fun stop() {
        Log.i(TAG, "Stopping mesh network")

        // Send leave announcement before stopping
        sendLeaveAnnouncement()

        gattServer?.stop()
        gattClient?.stop()
        peerManager.stop()

        gattServer = null
        gattClient = null

        _status.value = MeshStatus.Stopped
    }

    /**
     * Send a broadcast chat message to all peers.
     */
    fun sendMessage(text: String) {
        if (text.isBlank()) return
        if (_status.value != MeshStatus.Running) return

        val messagePayload = buildMessagePayload(text)
        val packet = HuzzPacket(
            type = MessageType.MESSAGE.value,
            ttl = MeshConstants.MAX_TTL.toUByte(),
            senderID = myPeerID.hexToByteArray(),
            payload = messagePayload
        )

        // Add to our own message list
        _messages.value = _messages.value + ChatMessage(
            sender = myNickname,
            senderPeerID = myPeerID,
            content = text,
            isFromMe = true
        )

        // Broadcast to all connected peers
        broadcastPacket(packet)
    }

    /**
     * Send a private message to a specific peer.
     */
    fun sendPrivateMessage(text: String, recipientPeerID: String) {
        if (text.isBlank()) return
        if (_status.value != MeshStatus.Running) return

        val messagePayload = buildMessagePayload(text)
        val packet = HuzzPacket(
            type = MessageType.PRIVATE.value,
            ttl = MeshConstants.MAX_TTL.toUByte(),
            senderID = myPeerID.hexToByteArray(),
            recipientID = recipientPeerID.hexToByteArray(),
            payload = messagePayload
        )

        val recipientNick = peerManager.getPeerNickname(recipientPeerID) ?: recipientPeerID.take(8)
        _messages.value = _messages.value + ChatMessage(
            sender = myNickname,
            senderPeerID = myPeerID,
            content = "[DM to $recipientNick] $text",
            isFromMe = true
        )

        broadcastPacket(packet)
    }

    /**
     * Clear all data (for panic mode).
     */
    fun clearAllData() {
        _messages.value = emptyList()
        peerManager.clearAllPeers()
        packetProcessor.clearAll()
        Log.w(TAG, "All mesh data cleared")
    }

    // --- Private: Packet Handling ---

    private fun handleIncomingPacket(packet: HuzzPacket, fromDevice: BluetoothDevice) {
        // Run through packet processor for dedup/TTL checks
        if (!packetProcessor.shouldProcess(packet)) return

        val senderHex = packet.senderID.toHexString()
        val type = MessageType.fromValue(packet.type)

        Log.d(TAG, "Received packet type=$type from=$senderHex ttl=${packet.ttl}")

        when (type) {
            MessageType.MESSAGE -> handleChatMessage(packet, senderHex, isPrivate = false)
            MessageType.PRIVATE -> {
                if (packetProcessor.isForUs(packet)) {
                    handleChatMessage(packet, senderHex, isPrivate = true)
                }
            }
            MessageType.ANNOUNCEMENT -> handleAnnouncement(packet, senderHex)
            MessageType.LEAVE -> handleLeave(senderHex)
            MessageType.PING -> {
                peerManager.getPeer(senderHex)?.let {
                    it.lastSeen = System.currentTimeMillis()
                }
            }
            null -> Log.w(TAG, "Unknown packet type: ${packet.type}")
        }

        // Relay if applicable
        if (packetProcessor.shouldRelay(packet)) {
            val relayPacket = packet.withDecrementedTtl()
            broadcastPacket(relayPacket, excludeAddress = fromDevice.address)
            Log.d(TAG, "Relayed packet from $senderHex, TTL now ${relayPacket.ttl}")
        }
    }

    private fun handleChatMessage(packet: HuzzPacket, senderHex: String, isPrivate: Boolean) {
        val payload = String(packet.payload, StandardCharsets.UTF_8)

        // Parse: "nickname\u0000message"
        val parts = payload.split("\u0000", limit = 2)
        val nickname = if (parts.size > 1) parts[0] else senderHex.take(8)
        val message = if (parts.size > 1) parts[1] else payload

        // Update peer nickname
        peerManager.addOrUpdatePeer(senderHex, nickname)

        val prefix = if (isPrivate) "[DM] " else ""
        _messages.value = _messages.value + ChatMessage(
            sender = nickname,
            senderPeerID = senderHex,
            content = prefix + message,
            isFromMe = false,
            isRelayed = packet.ttl.toInt() < MeshConstants.MAX_TTL
        )
    }

    private fun handleAnnouncement(packet: HuzzPacket, senderHex: String) {
        val nickname = String(packet.payload, StandardCharsets.UTF_8)
        val isNew = peerManager.addOrUpdatePeer(senderHex, nickname)
        if (isNew) {
            _messages.value = _messages.value + ChatMessage(
                sender = "System",
                senderPeerID = "",
                content = "📡 $nickname joined the mesh",
                isFromMe = false
            )
        }
    }

    private fun handleLeave(senderHex: String) {
        val nickname = peerManager.getPeerNickname(senderHex) ?: senderHex.take(8)
        peerManager.markDisconnected(senderHex)
        _messages.value = _messages.value + ChatMessage(
            sender = "System",
            senderPeerID = "",
            content = "👋 $nickname left the mesh",
            isFromMe = false
        )
    }

    // --- Private: Connection Events ---

    private fun handlePeerDiscovered(address: String, peerID: String, rssi: Int) {
        if (peerID.isNotBlank()) {
            peerManager.updatePeerRSSI(peerID, rssi)
        }
    }

    private fun handleDeviceConnected(device: BluetoothDevice, peerID: String?) {
        if (peerID != null && peerID.isNotBlank()) {
            peerManager.setDirectConnection(peerID, true)
            Log.i(TAG, "Direct BLE connection established with $peerID")

            // Send our announcement to the new peer
            meshScope.launch {
                delay(500)
                sendAnnouncement()
            }
        }
    }

    private fun handleDeviceDisconnected(device: BluetoothDevice) {
        val address = device.address
        // Find peer by device address
        val peerID = gattClient?.getPeerIDForAddress(address)
        if (peerID != null) {
            peerManager.setDirectConnection(peerID, false)
            Log.i(TAG, "BLE connection lost with $peerID")
        }
    }

    // --- Private: Sending ---

    /**
     * Send an announcement packet with our nickname.
     */
    private fun sendAnnouncement() {
        val packet = HuzzPacket(
            type = MessageType.ANNOUNCEMENT.value,
            ttl = MeshConstants.MAX_TTL.toUByte(),
            senderID = myPeerID.hexToByteArray(),
            payload = myNickname.toByteArray(StandardCharsets.UTF_8)
        )
        broadcastPacket(packet)
    }

    /**
     * Send a leave announcement before disconnecting.
     */
    private fun sendLeaveAnnouncement() {
        val packet = HuzzPacket(
            type = MessageType.LEAVE.value,
            ttl = MeshConstants.MAX_TTL.toUByte(),
            senderID = myPeerID.hexToByteArray(),
            payload = myNickname.toByteArray(StandardCharsets.UTF_8)
        )
        broadcastPacket(packet)
    }

    /**
     * Broadcast a packet to all connected peers via both server and client connections.
     */
    private fun broadcastPacket(packet: HuzzPacket, excludeAddress: String? = null) {
        val data = packet.toBinaryData()

        // Send via GATT Server notifications (to devices connected to us)
        gattServer?.broadcastToConnectedDevices(data)

        // Send via GATT Client writes (to devices we connected to)
        if (excludeAddress != null) {
            val addresses = gattClient?.getConnectedDeviceAddresses() ?: emptySet()
            addresses.filter { it != excludeAddress }.forEach { address ->
                gattClient?.writeToDevice(address, data)
            }
        } else {
            gattClient?.broadcastToAllConnected(data)
        }
    }

    /**
     * Build a message payload: "nickname\0message"
     */
    private fun buildMessagePayload(message: String): ByteArray {
        return "$myNickname\u0000$message".toByteArray(StandardCharsets.UTF_8)
    }

    /**
     * Generate a random 8-byte peer ID as a hex string.
     */
    private fun generatePeerID(): String {
        val uuid = UUID.randomUUID()
        val bytes = ByteArray(8)
        val msb = uuid.mostSignificantBits
        for (i in 0 until 8) {
            bytes[i] = (msb shr (56 - i * 8) and 0xFF).toByte()
        }
        return bytes.toHexString()
    }
}
