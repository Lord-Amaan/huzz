package com.example.huzz.model

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * HuzzFilePacket: TLV-encoded file transfer payload for BLE mesh and offline transfers.
 * TLVs:
 *  - 0x01: filename (UTF-8)
 *  - 0x02: file size (4 bytes, UInt32)
 *  - 0x03: mime type (UTF-8)
 *  - 0x04: content (bytes) - may appear multiple times for large files
 *
 * Length field for TLV is 2 bytes (UInt16, big-endian) for metadata TLVs,
 * and 4 bytes (UInt32, big-endian) for CONTENT TLV.
 *
 * Unknown TLV types are skipped for forward compatibility.
 */
data class HuzzFilePacket(
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val content: ByteArray,
    val channel: String? = null
) {
    private enum class TLVType(val v: UByte) {
        FILE_NAME(0x01u), FILE_SIZE(0x02u), MIME_TYPE(0x03u), CONTENT(0x04u), CHANNEL(0x05u);
        companion object {
            fun from(value: UByte): TLVType? = when (value) {
                FILE_NAME.v -> FILE_NAME
                FILE_SIZE.v -> FILE_SIZE
                MIME_TYPE.v -> MIME_TYPE
                CONTENT.v -> CONTENT
                CHANNEL.v -> CHANNEL
                else -> null
            }
        }
    }

    fun encode(): ByteArray? {
        try {
            android.util.Log.d("HuzzFilePacket", "Encoding: name=$fileName, size=$fileSize, mime=$mimeType, channel=$channel")
            val nameBytes = fileName.toByteArray(Charsets.UTF_8)
            val mimeBytes = mimeType.toByteArray(Charsets.UTF_8)
            val channelBytes = channel?.toByteArray(Charsets.UTF_8)
            
            if (nameBytes.size > 0xFFFF || mimeBytes.size > 0xFFFF || (channelBytes != null && channelBytes.size > 0xFFFF)) {
                android.util.Log.e("HuzzFilePacket", "TLV field too large: name=${nameBytes.size}, mime=${mimeBytes.size} (max: 65535)")
                return null
            }
            if (content.size > 0xFFFF) {
                android.util.Log.d("HuzzFilePacket", "Content exceeds 65535 bytes (${content.size}); will be packaged into 4-byte length CONTENT TLV")
            }
            val sizeFieldLen = 4 // UInt32 for FILE_SIZE
            val contentLenFieldLen = 4 // UInt32 for CONTENT TLV

            // Compute capacity: header TLVs + single CONTENT TLV with 4-byte length
            val contentTLVBytes = 1 + contentLenFieldLen + content.size
            val channelTLVBytes = if (channelBytes != null) (1 + 2 + channelBytes.size) else 0
            val capacity = (1 + 2 + nameBytes.size) + (1 + 2 + sizeFieldLen) + (1 + 2 + mimeBytes.size) + channelTLVBytes + contentTLVBytes
            val buf = ByteBuffer.allocate(capacity).order(ByteOrder.BIG_ENDIAN)

            // FILE_NAME
            buf.put(TLVType.FILE_NAME.v.toByte())
            buf.putShort(nameBytes.size.toShort())
            buf.put(nameBytes)

            // FILE_SIZE (4 bytes)
            buf.put(TLVType.FILE_SIZE.v.toByte())
            buf.putShort(sizeFieldLen.toShort())
            buf.putInt(fileSize.toInt())

            // MIME_TYPE
            buf.put(TLVType.MIME_TYPE.v.toByte())
            buf.putShort(mimeBytes.size.toShort())
            buf.put(mimeBytes)

            // CHANNEL (optional)
            if (channelBytes != null) {
                buf.put(TLVType.CHANNEL.v.toByte())
                buf.putShort(channelBytes.size.toShort())
                buf.put(channelBytes)
            }

            // CONTENT (single TLV with 4-byte length)
            buf.put(TLVType.CONTENT.v.toByte())
            buf.putInt(content.size)
            buf.put(content)

            val result = buf.array()
            android.util.Log.d("HuzzFilePacket", "Encoded successfully: ${result.size} bytes total")
            return result
        } catch (e: Exception) {
            android.util.Log.e("HuzzFilePacket", "Encoding failed: ${e.message}", e)
            return null
        }
    }

    companion object {
        fun decode(data: ByteArray): HuzzFilePacket? {
            android.util.Log.d("HuzzFilePacket", "Decoding ${data.size} bytes")
            try {
                var off = 0
                var name: String? = null
                var size: Long? = null
                var mime: String? = null
                var channel: String? = null
                var contentBytes: ByteArray? = null
                var skippedUnknownTLVs = 0
                while (off < data.size) {
                    if (data.size - off < 3) return null
                    val t = TLVType.from(data[off].toUByte())
                    off += 1
                    
                    val len: Int
                    if (t == TLVType.CONTENT) {
                        if (off + 4 > data.size) return null
                        len = ((data[off].toInt() and 0xFF) shl 24) or 
                              ((data[off + 1].toInt() and 0xFF) shl 16) or 
                              ((data[off + 2].toInt() and 0xFF) shl 8) or 
                              (data[off + 3].toInt() and 0xFF)
                        off += 4
                    } else {
                        if (off + 2 > data.size) return null
                        len = ((data[off].toInt() and 0xFF) shl 8) or (data[off + 1].toInt() and 0xFF)
                        off += 2
                    }
                    if (len < 0 || off + len > data.size) return null
                    if (t == null) {
                        off += len
                        skippedUnknownTLVs += 1
                        continue
                    }
                    val value = data.copyOfRange(off, off + len)
                    off += len
                    when (t) {
                        TLVType.FILE_NAME -> name = String(value, Charsets.UTF_8)
                        TLVType.FILE_SIZE -> {
                            if (len != 4) return null
                            val bb = ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN)
                            size = bb.int.toLong()
                        }
                        TLVType.MIME_TYPE -> mime = String(value, Charsets.UTF_8)
                        TLVType.CHANNEL -> channel = String(value, Charsets.UTF_8)
                        TLVType.CONTENT -> {
                            if (contentBytes == null) contentBytes = value else {
                                contentBytes = (contentBytes!! + value)
                            }
                        }
                    }
                }
                if (skippedUnknownTLVs > 0) {
                    android.util.Log.d("HuzzFilePacket", "Skipped $skippedUnknownTLVs unknown TLV(s)")
                }
                val n = name ?: return null
                val c = contentBytes ?: return null
                val s = size ?: c.size.toLong()
                val m = mime ?: "application/octet-stream"
                val result = HuzzFilePacket(n, s, m, c, channel)
                android.util.Log.d("HuzzFilePacket", "Decoded: name=$n, size=$s, mime=$m, channel=$channel, content=${c.size} bytes")
                return result
            } catch (e: Exception) {
                android.util.Log.e("HuzzFilePacket", "Decoding failed: ${e.message}", e)
                return null
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HuzzFilePacket

        if (fileName != other.fileName) return false
        if (fileSize != other.fileSize) return false
        if (mimeType != other.mimeType) return false
        if (channel != other.channel) return false
        if (!content.contentEquals(other.content)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + fileSize.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + (channel?.hashCode() ?: 0)
        result = 31 * result + content.contentHashCode()
        return result
    }
}
