package com.example.huzz.mesh

import java.util.concurrent.ConcurrentHashMap

/**
 * Multi-hop routing table and packet forwarding coordinator for Huzz BLE mesh.
 */
class MeshRouter(private val myPeerId: String) {

    data class RouteEntry(
        val destinationPeerId: String,
        val nextHopPeerId: String,
        val hopCount: Int,
        val lastUpdated: Long = System.currentTimeMillis()
    )

    private val routingTable = ConcurrentHashMap<String, RouteEntry>()
    private val seenPacketCache = ConcurrentHashMap<String, Long>()

    /**
     * Determines whether an incoming packet should be forwarded.
     */
    fun shouldForward(packet: HuzzPacket): Boolean {
        // If seen recently, do not forward (deduplication)
        val now = System.currentTimeMillis()
        if (seenPacketCache.containsKey(packet.packetId)) {
            return false
        }
        seenPacketCache[packet.packetId] = now
        pruneSeenCache(now)

        // Do not forward if TTL is depleted
        if (!MeshUtils.canForwardPacket(packet.ttl.toInt())) {
            return false
        }

        // If direct recipient is specified and is me, no need to forward
        val recipientHex = packet.recipientID?.let { MeshSecurity.toHexString(it) }
        if (recipientHex != null && recipientHex == myPeerId) {
            return false
        }

        return true
    }

    /**
     * Updates or inserts a route to destination peer.
     */
    fun updateRoute(destinationPeerId: String, nextHopPeerId: String, hopCount: Int) {
        val existing = routingTable[destinationPeerId]
        if (existing == null || hopCount < existing.hopCount || (System.currentTimeMillis() - existing.lastUpdated > MeshConstants.STALE_PEER_TIMEOUT_MS)) {
            routingTable[destinationPeerId] = RouteEntry(destinationPeerId, nextHopPeerId, hopCount)
        }
    }

    /**
     * Finds next hop for a target peer ID.
     */
    fun getNextHop(destinationPeerId: String): String? {
        return routingTable[destinationPeerId]?.nextHopPeerId
    }

    private fun pruneSeenCache(now: Long) {
        if (seenPacketCache.size > MeshConstants.SEEN_CACHE_MAX_SIZE) {
            val iterator = seenPacketCache.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value > MeshConstants.SEEN_CACHE_EXPIRY_MS) {
                    iterator.remove()
                }
            }
        }
    }
}
