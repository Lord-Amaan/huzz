package com.example.huzz.mesh

/**
 * Utility helpers for Huzz BLE mesh operations.
 */
object MeshUtils {

    /**
     * Categorizes RSSI signal strength into human-readable signal quality tiers.
     */
    fun getSignalStrength(rssi: Int): SignalQuality {
        return when {
            rssi >= -60 -> SignalQuality.EXCELLENT
            rssi >= -75 -> SignalQuality.GOOD
            rssi >= -85 -> SignalQuality.FAIR
            else -> SignalQuality.POOR
        }
    }

    /**
     * Checks if a packet TTL is valid for forwarding through the mesh network.
     */
    fun canForwardPacket(ttl: Int): Boolean {
        return ttl in 1..MeshConstants.MAX_TTL
    }

    /**
     * Formats peer ID to a truncated display string.
     */
    fun formatDisplayPeerId(peerId: String): String {
        return if (peerId.length > 8) peerId.take(8) else peerId
    }

    enum class SignalQuality {
        EXCELLENT,
        GOOD,
        FAIR,
        POOR
    }
}
