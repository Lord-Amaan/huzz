package com.example.huzz.mesh

import java.util.concurrent.PriorityBlockingQueue

/**
 * Prioritized outbound packet queue ensuring vital mesh traffic is dispatched first.
 */
class PacketPriorityQueue(capacity: Int = 100) {

    data class PrioritizedPacket(
        val packet: HuzzPacket,
        val priority: Int, // Higher number = higher priority
        val enqueuedTime: Long = System.currentTimeMillis()
    ) : Comparable<PrioritizedPacket> {
        override fun compareTo(other: PrioritizedPacket): Int {
            val pCompare = other.priority.compareTo(this.priority)
            return if (pCompare != 0) pCompare else this.enqueuedTime.compareTo(other.enqueuedTime)
        }
    }

    private val queue = PriorityBlockingQueue<PrioritizedPacket>(capacity)

    /**
     * Enqueues a packet with computed priority level based on message type.
     */
    fun enqueue(packet: HuzzPacket) {
        val priority = when (MessageType.fromValue(packet.type)) {
            MessageType.PRIVATE -> 100
            MessageType.MESSAGE -> 80
            MessageType.ANNOUNCEMENT -> 50
            MessageType.LEAVE -> 40
            MessageType.PING -> 10
            null -> 0
        }
        queue.offer(PrioritizedPacket(packet, priority))
    }

    /**
     * Retrieves and removes the highest priority packet, or null if empty.
     */
    fun poll(): HuzzPacket? {
        return queue.poll()?.packet
    }

    /**
     * Current queue size.
     */
    val size: Int
        get() = queue.size

    /**
     * Clears all pending packets in the queue.
     */
    fun clear() {
        queue.clear()
    }
}
