package com.example.huzz.mesh

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Message types for the Huzz mesh protocol.
 * Based on BitChat's MessageType enum.
 */
enum class MessageType(val value: UByte) {
    MESSAGE(0x01u),         // Regular chat message
    ANNOUNCEMENT(0x02u),    // Peer announcement (nickname broadcast)
    LEAVE(0x03u),           // Peer leaving the mesh
    PING(0x04u),            // Keep-alive ping
    PRIVATE(0x05u);         // Private/direct message

    companion object {
        fun fromValue(value: UByte): MessageType? = entries.find { it.value == value }
    }
}

/**
 * Represents a single packet in the Huzz mesh protocol.
 * Binary format (Big Endian):
 *   [version: 1B] [type: 1B] [ttl: 1B] [timestamp: 8B] [flags: 1B]
 *   [payloadLength: 2B] [senderID: 8B] [recipientID?: 8B] [payload: NB]
 *
 * Flags:
 *   bit 0: hasRecipient
 */
data class HuzzPacket(
    val version: UByte = 1u,
    val type: UByte,
    val ttl: UByte,
    val timestamp: ULong = System.currentTimeMillis().toULong(),
    val senderID: ByteArray,                // 8 bytes
    val recipientID: ByteArray? = null,     // 8 bytes, null = broadcast
    val payload: ByteArray,
    val packetId: String = UUID.randomUUID().toString().take(8) // For dedup
) {
    companion object {
        private const val HEADER_SIZE = 14  // 1+1+1+8+1+2
        private const val FLAGS_HAS_RECIPIENT: UByte = 0x01u

        /**
         * Decode a HuzzPacket from raw binary data.
         */
        fun fromBinaryData(data: ByteArray): HuzzPacket? {
            try {
                if (data.size < HEADER_SIZE + MeshConstants.PEER_ID_SIZE) return null

                val buffer = ByteBuffer.wrap(data).apply { order(ByteOrder.BIG_ENDIAN) }

                val version = buffer.get().toUByte()
                if (version.toUInt() != 1u) return null

                val type = buffer.get().toUByte()
                val ttl = buffer.get().toUByte()
                val timestamp = buffer.getLong().toULong()
                val flags = buffer.get().toUByte()
                val hasRecipient = (flags and FLAGS_HAS_RECIPIENT) != 0u.toUByte()
                val payloadLength = buffer.getShort().toUShort().toInt()

                // SenderID
                val senderID = ByteArray(MeshConstants.PEER_ID_SIZE)
                buffer.get(senderID)

                // RecipientID (optional)
                val recipientID = if (hasRecipient) {
                    val recipientBytes = ByteArray(MeshConstants.PEER_ID_SIZE)
                    buffer.get(recipientBytes)
                    recipientBytes
                } else null

                // Payload
                if (buffer.remaining() < payloadLength) return null
                val payload = ByteArray(payloadLength)
                buffer.get(payload)

                // Derive a packetId from sender + timestamp for dedup
                val packetId = senderID.toHexString().take(8) + timestamp.toString().takeLast(8)

                return HuzzPacket(
                    version = version,
                    type = type,
                    ttl = ttl,
                    timestamp = timestamp,
                    senderID = senderID,
                    recipientID = recipientID,
                    payload = payload,
                    packetId = packetId
                )
            } catch (e: Exception) {
                return null
            }
        }
    }

    /**
     * Encode this packet to binary data for BLE transmission.
     */
    fun toBinaryData(): ByteArray {
        val hasRecipient = recipientID != null
        val flags: UByte = if (hasRecipient) FLAGS_HAS_RECIPIENT else 0u

        val totalSize = HEADER_SIZE + MeshConstants.PEER_ID_SIZE +
                (if (hasRecipient) MeshConstants.PEER_ID_SIZE else 0) +
                payload.size

        val buffer = ByteBuffer.allocate(totalSize).apply { order(ByteOrder.BIG_ENDIAN) }

        buffer.put(version.toByte())
        buffer.put(type.toByte())
        buffer.put(ttl.toByte())
        buffer.putLong(timestamp.toLong())
        buffer.put(flags.toByte())
        buffer.putShort(payload.size.toShort())
        buffer.put(senderID)
        if (hasRecipient && recipientID != null) {
            buffer.put(recipientID)
        }
        buffer.put(payload)

        return buffer.array()
    }

    /**
     * Create a copy of this packet with TTL decremented by 1 (for relaying).
     */
    fun withDecrementedTtl(): HuzzPacket {
        return copy(ttl = (ttl.toInt() - 1).coerceAtLeast(0).toUByte())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as HuzzPacket
        return packetId == other.packetId
    }

    override fun hashCode(): Int = packetId.hashCode()
}

// Extension to convert ByteArray to hex string
fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

// Extension to convert hex string to ByteArray
fun String.hexToByteArray(): ByteArray {
    val result = ByteArray(MeshConstants.PEER_ID_SIZE) { 0 }
    var index = 0
    var temp = this
    while (temp.length >= 2 && index < MeshConstants.PEER_ID_SIZE) {
        val hexByte = temp.substring(0, 2)
        result[index] = hexByte.toIntOrNull(16)?.toByte() ?: 0
        temp = temp.substring(2)
        index++
    }
    return result
}
