package com.example.huzz.model

/**
 * Emergency status categories for disaster distress beacons.
 */
enum class EmergencyStatus(val displayName: String, val shortCode: String) {
    MEDICAL("Medical Emergency", "MED"),
    TRAPPED("Trapped / Search & Rescue", "TRP"),
    EVACUATING("Evacuating Area", "EVAC"),
    ASSISTANCE("Need Assistance", "AST"),
    CANCELLED("Resolved / Safe", "SAFE");

    companion object {
        fun fromString(value: String): EmergencyStatus {
            val clean = value.trim().uppercase()
            return entries.firstOrNull {
                it.name == clean || it.shortCode == clean || it.displayName.equals(clean, ignoreCase = true)
            } ?: ASSISTANCE
        }
    }
}

/**
 * High-priority emergency distress beacon payload.
 * Fits within an unfragmented single packet (<= 200 bytes).
 * Serialized wire format: SOS:v1|<status>|<battery>|<geohash>|<note>
 */
data class SosPayload(
    val status: EmergencyStatus,
    val batteryPct: Int,
    val geohash: String?,
    val note: String,
    val timestampMs: Long = System.currentTimeMillis()
) {
    /**
     * Encode payload to compact wire bytes:
     * SOS:v1|<status>|<battery>|<geohash>|<note>
     */
    fun encode(): ByteArray {
        val safeGeohash = geohash?.trim() ?: ""
        val safeNote = note.replace("|", "/").replace("\n", " ").trim().take(120)
        val wireString = "SOS:v1|${status.name}|$batteryPct|$safeGeohash|$safeNote"
        return wireString.toByteArray(Charsets.UTF_8)
    }

    companion object {
        private const val PREFIX = "SOS:v1|"

        fun isSosPayload(payload: ByteArray): Boolean {
            if (payload.size < PREFIX.length) return false
            val prefixBytes = PREFIX.toByteArray(Charsets.UTF_8)
            for (i in prefixBytes.indices) {
                if (payload[i] != prefixBytes[i]) return false
            }
            return true
        }

        fun decode(bytes: ByteArray, timestampMs: Long = System.currentTimeMillis()): SosPayload? {
            return try {
                val str = String(bytes, Charsets.UTF_8)
                if (!str.startsWith(PREFIX)) return null
                val parts = str.split("|", limit = 5)
                if (parts.size < 5) return null
                val status = EmergencyStatus.fromString(parts[1])
                val batteryPct = parts[2].toIntOrNull() ?: -1
                val geohash = parts[3].takeIf { it.isNotBlank() }
                val note = parts[4]
                SosPayload(
                    status = status,
                    batteryPct = batteryPct,
                    geohash = geohash,
                    note = note,
                    timestampMs = timestampMs
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
