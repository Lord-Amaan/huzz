package com.example.huzz.mesh

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Security and payload integrity verification utilities for Huzz mesh packets.
 */
object MeshSecurity {

    private val secureRandom = SecureRandom()

    /**
     * Computes SHA-256 checksum for arbitrary packet payload.
     */
    fun computeChecksum(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data)
    }

    /**
     * Verifies if the payload matches an expected SHA-256 checksum.
     */
    fun verifyChecksum(data: ByteArray, expectedChecksum: ByteArray): Boolean {
        val calculated = computeChecksum(data)
        return MessageDigest.isEqual(calculated, expectedChecksum)
    }

    /**
     * Generates a cryptographically secure random token of specified byte length.
     */
    fun generateSecureNonce(length: Int = 16): ByteArray {
        val nonce = ByteArray(length)
        secureRandom.nextBytes(nonce)
        return nonce
    }

    /**
     * Formats bytes to hex string for debug tracing.
     */
    fun toHexString(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
