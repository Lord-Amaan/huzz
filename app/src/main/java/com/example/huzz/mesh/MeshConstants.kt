package com.example.huzz.mesh

import java.util.UUID

/**
 * Centralized constants for the Huzz BLE mesh protocol.
 * Inspired by BitChat's AppConstants architecture.
 */
object MeshConstants {

    // --- BLE GATT UUIDs ---
    // Custom UUIDs for our mesh service. These must be unique to Huzz.
    val SERVICE_UUID: UUID = UUID.fromString("E47B5E2D-4A9E-4C5A-9B3F-8E1D2C3A4B5C")
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("B1B2C3D4-E5F6-4A5B-8C9D-0E1F2A3B4C5D")
    val DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb") // Standard CCCD

    // --- Peer lifecycle ---
    const val STALE_PEER_TIMEOUT_MS: Long = 180_000L      // 3 minutes without activity = stale
    const val PEER_CLEANUP_INTERVAL_MS: Long = 60_000L    // Check for stale peers every 60s

    // --- BLE scanning ---
    const val SCAN_ON_DURATION_MS: Long = 8_000L          // Scan for 8 seconds
    const val SCAN_OFF_DURATION_MS: Long = 2_000L         // Pause for 2 seconds

    // --- BLE connection ---
    const val MAX_CONNECTIONS: Int = 7                     // Android BLE connection limit
    const val CONNECTION_RETRY_DELAY_MS: Long = 5_000L
    const val MTU_SIZE: Int = 512                          // Request 512 byte MTU

    // --- Packet protocol ---
    const val MAX_TTL: Int = 7                             // Maximum hops a packet can travel
    const val PEER_ID_SIZE: Int = 8                        // 8 bytes for peer ID
    const val MAX_PAYLOAD_SIZE: Int = 65535                // Max payload in bytes (UShort max)
    const val SEEN_CACHE_MAX_SIZE: Int = 10_000            // Max entries in seen-message dedup cache
    const val SEEN_CACHE_EXPIRY_MS: Long = 300_000L        // 5 minutes dedup window

    // --- Foreground service ---
    const val NOTIFICATION_CHANNEL_ID = "huzz_mesh_channel"
    const val NOTIFICATION_ID = 1001
    const val FOREGROUND_SERVICE_TYPE = "connectedDevice"
}
