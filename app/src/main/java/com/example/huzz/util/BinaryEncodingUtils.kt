package com.example.huzz.util

import java.util.Date

// MARK: - Hex Encoding/Decoding Extensions

fun ByteArray.hexEncodedString(): String {
    if (this.isEmpty()) {
        return ""
    }
    return this.joinToString("") { "%02x".format(it) }
}

fun String.dataFromHexString(): ByteArray? {
    val len = this.length / 2
    val data = ByteArray(len)
    var index = 0
    
    for (i in 0 until len) {
        val hexByte = this.substring(i * 2, i * 2 + 2)
        val byte = hexByte.toIntOrNull(16)?.toByte() ?: return null
        data[index++] = byte
    }
    
    return data
}

// MARK: - Binary Encoding Utilities

class BinaryDataBuilder {
    private val _buffer = mutableListOf<Byte>()
    
    val buffer: MutableList<Byte> get() = _buffer
    
    fun appendUInt8(value: UByte) {
        buffer.add(value.toByte())
    }
    
    fun appendUInt16(value: UShort) {
        buffer.add(((value.toInt() shr 8) and 0xFF).toByte())
        buffer.add((value.toInt() and 0xFF).toByte())
    }
    
    fun appendUInt32(value: UInt) {
        buffer.add(((value.toLong() shr 24) and 0xFF).toByte())
        buffer.add(((value.toLong() shr 16) and 0xFF).toByte())
        buffer.add(((value.toLong() shr 8) and 0xFF).toByte())
        buffer.add((value.toLong() and 0xFF).toByte())
    }
    
    fun appendUInt64(value: ULong) {
        for (i in 7 downTo 0) {
            buffer.add(((value.toLong() shr (i * 8)) and 0xFF).toByte())
        }
    }
    
    fun appendString(string: String, maxLength: Int = 255) {
        val data = string.toByteArray(Charsets.UTF_8)
        val length = minOf(data.size, maxLength)
        
        if (maxLength <= 255) {
            buffer.add(length.toByte())
        } else {
            appendUInt16(length.toUShort())
        }
        
        buffer.addAll(data.take(length).toList())
    }
    
    fun appendData(data: ByteArray, maxLength: Int = 65535) {
        val length = minOf(data.size, maxLength)
        
        if (maxLength <= 255) {
            buffer.add(length.toByte())
        } else {
            appendUInt16(length.toUShort())
        }
        
        buffer.addAll(data.take(length).toList())
    }
    
    fun appendDate(date: Date) {
        val timestamp = (date.time).toULong()
        appendUInt64(timestamp)
    }
    
    fun appendUUID(uuid: String) {
        val uuidData = ByteArray(16)
        val cleanUUID = uuid.replace("-", "")
        var index = 0
        
        for (i in 0 until 16) {
            if (index + 1 < cleanUUID.length) {
                val hexByte = cleanUUID.substring(index, index + 2)
                uuidData[i] = hexByte.toIntOrNull(16)?.toByte() ?: 0
                index += 2
            }
        }
        
        buffer.addAll(uuidData.toList())
    }
    
    fun toByteArray(): ByteArray {
        return buffer.toByteArray()
    }
}

// MARK: - Binary Data Reading Extensions

class BinaryDataReader(private val data: ByteArray) {
    private var offset = 0
    
    fun readUInt8(): UByte? {
        if (offset >= data.size) return null
        val value = data[offset].toUByte()
        offset += 1
        return value
    }
    
    fun readUInt16(): UShort? {
        if (offset + 2 > data.size) return null
        val value = ((data[offset].toUByte().toInt() shl 8) or 
                    (data[offset + 1].toUByte().toInt())).toUShort()
        offset += 2
        return value
    }
    
    fun readUInt32(): UInt? {
        if (offset + 4 > data.size) return null
        val value = ((data[offset].toUByte().toUInt() shl 24) or
                    (data[offset + 1].toUByte().toUInt() shl 16) or
                    (data[offset + 2].toUByte().toUInt() shl 8) or
                    (data[offset + 3].toUByte().toUInt()))
        offset += 4
        return value
    }
    
    fun readUInt64(): ULong? {
        if (offset + 8 > data.size) return null
        var value = 0UL
        for (i in 0 until 8) {
            value = (value shl 8) or data[offset + i].toUByte().toULong()
        }
        offset += 8
        return value
    }
    
    fun readString(maxLength: Int = 255): String? {
        val length: Int = if (maxLength <= 255) {
            readUInt8()?.toInt() ?: return null
        } else {
            readUInt16()?.toInt() ?: return null
        }
        
        if (offset + length > data.size) return null
        
        val stringData = data.sliceArray(offset until offset + length)
        offset += length
        
        return String(stringData, Charsets.UTF_8)
    }
    
    fun readData(maxLength: Int = 65535): ByteArray? {
        val length: Int = if (maxLength <= 255) {
            readUInt8()?.toInt() ?: return null
        } else {
            readUInt16()?.toInt() ?: return null
        }
        
        if (offset + length > data.size) return null
        
        val data = this.data.sliceArray(offset until offset + length)
        offset += length
        
        return data
    }
    
    fun readDate(): Date? {
        val timestamp = readUInt64() ?: return null
        return Date(timestamp.toLong())
    }
    
    fun readUUID(): String? {
        if (offset + 16 > data.size) return null
        
        val uuidData = data.sliceArray(offset until offset + 16)
        offset += 16
        
        val uuid = uuidData.joinToString("") { "%02x".format(it) }
        val result = StringBuilder()
        for ((index, char) in uuid.withIndex()) {
            if (index == 8 || index == 12 || index == 16 || index == 20) {
                result.append("-")
            }
            result.append(char)
        }
        
        return result.toString().uppercase()
    }
    
    fun readFixedBytes(count: Int): ByteArray? {
        if (offset + count > data.size) return null
        
        val data = this.data.sliceArray(offset until offset + count)
        offset += count
        
        return data
    }
    
    val currentOffset: Int get() = offset
}

interface BinaryEncodable {
    fun toBinaryData(): ByteArray
}

enum class BinaryMessageType(val value: UByte) {
    DELIVERY_ACK(0x01u),
    READ_RECEIPT(0x02u),
    CHANNEL_KEY_VERIFY_REQUEST(0x03u),
    CHANNEL_KEY_VERIFY_RESPONSE(0x04u),
    CHANNEL_PASSWORD_UPDATE(0x05u),
    CHANNEL_METADATA(0x06u),
    VERSION_HELLO(0x07u),
    VERSION_ACK(0x08u),
    NOISE_IDENTITY_ANNOUNCEMENT(0x09u),
    NOISE_MESSAGE(0x0Au);
    
    companion object {
        fun fromValue(value: UByte): BinaryMessageType? {
            return entries.find { it.value == value }
        }
    }
}
