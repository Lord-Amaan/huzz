package com.example.huzz.mesh

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles packet deduplication, TTL enforcement, and relay decisions.
 * Uses a seen-message cache to prevent infinite loops in the mesh.
 *
 * Adapted from BitChat's PacketProcessor and SecurityManager.
 */
class PacketProcessor(private val myPeerID: String) {

    companion object {
        private const val TAG = "PacketProcessor"
    }

    /**
     * Cache of recently seen packet IDs with their arrival timestamp.
     * Used to prevent reprocessing/relaying the same packet.
     */
    private val seenPackets = ConcurrentHashMap<String, Long>()

    /**
     * Check if we should process this packet.
     * Returns true if the packet is new and valid.
     */
    fun shouldProcess(packet: HuzzPacket): Boolean {
        // Ignore our own packets
        val senderHex = packet.senderID.toHexString()
        if (senderHex == myPeerID) {
            return false
        }

        // Check TTL
        if (packet.ttl.toInt() <= 0) {
            Log.d(TAG, "Dropping packet with expired TTL from $senderHex")
            return false
        }

        // Dedup check
        if (seenPackets.containsKey(packet.packetId)) {
            Log.d(TAG, "Dropping duplicate packet ${packet.packetId}")
            return false
        }

        // Mark as seen
        seenPackets[packet.packetId] = System.currentTimeMillis()

        return true
    }

    /**
     * Check if a packet should be relayed to other peers.
     * Returns true if TTL > 1 (still has hops left after decrement).
     */
    fun shouldRelay(packet: HuzzPacket): Boolean {
        // Don't relay if TTL would hit 0
        if (packet.ttl.toInt() <= 1) return false

        // Don't relay packets addressed directly to us
        if (packet.recipientID != null) {
            val recipientHex = packet.recipientID.toHexString()
            if (recipientHex == myPeerID) return false
        }

        return true
    }

    /**
     * Check if a packet is addressed to us (or is a broadcast).
     */
    fun isForUs(packet: HuzzPacket): Boolean {
        // Broadcast packets are for everyone
        if (packet.recipientID == null) return true

        // Check if we're the recipient
        val recipientHex = packet.recipientID.toHexString()
        return recipientHex == myPeerID
    }

    /**
     * Periodic cleanup of old entries from the seen cache.
     */
    fun cleanupSeenCache() {
        val now = System.currentTimeMillis()
        val expired = seenPackets.filter {
            (now - it.value) > MeshConstants.SEEN_CACHE_EXPIRY_MS
        }
        expired.keys.forEach { seenPackets.remove(it) }

        // Also enforce max size by removing oldest entries
        if (seenPackets.size > MeshConstants.SEEN_CACHE_MAX_SIZE) {
            val toRemove = seenPackets.entries
                .sortedBy { it.value }
                .take(seenPackets.size - MeshConstants.SEEN_CACHE_MAX_SIZE)
            toRemove.forEach { seenPackets.remove(it.key) }
        }
    }

    /**
     * Clear all seen packets (for panic mode).
     */
    fun clearAll() {
        seenPackets.clear()
    }

    fun getSeenCount(): Int = seenPackets.size
}
