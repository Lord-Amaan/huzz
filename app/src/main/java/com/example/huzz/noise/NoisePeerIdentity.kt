package com.example.huzz.noise

import java.security.MessageDigest

/**
 * Canonical binding between an authenticated Noise static key and its mesh wire identity.
 *
 * Mesh peer IDs are the first eight bytes of SHA-256(staticPublicKey), encoded as 16 lowercase
 * hexadecimal characters.
 */
object NoisePeerIdentity {
    const val STATIC_PUBLIC_KEY_SIZE = 32
    const val WIRE_PEER_ID_LENGTH = 16

    private val wirePeerIDPattern = Regex("^[0-9a-f]{$WIRE_PEER_ID_LENGTH}$")

    fun derivePeerID(staticPublicKey: ByteArray): String? {
        if (staticPublicKey.size != STATIC_PUBLIC_KEY_SIZE) return null
        return MessageDigest.getInstance("SHA-256")
            .digest(staticPublicKey)
            .take(8)
            .joinToString("") { "%02x".format(it) }
    }

    fun matchesClaimedPeerID(claimedPeerID: String, staticPublicKey: ByteArray): Boolean {
        if (!wirePeerIDPattern.matches(claimedPeerID)) return false
        return derivePeerID(staticPublicKey) == claimedPeerID
    }
}
