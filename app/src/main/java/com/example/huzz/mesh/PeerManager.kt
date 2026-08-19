package com.example.huzz.mesh

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Represents a peer in the mesh network.
 */
data class MeshPeer(
    val id: String,                    // Hex string peer ID
    var nickname: String,
    var isConnected: Boolean = false,
    var isDirectConnection: Boolean = false,
    var lastSeen: Long = System.currentTimeMillis(),
    var rssi: Int = 0
)

/**
 * Manages active peers in the mesh network.
 * Tracks peer discovery, connection state, nicknames, and RSSI.
 * Handles periodic cleanup of stale peers.
 *
 * Adapted from BitChat's PeerManager.
 */
class PeerManager(private val scope: CoroutineScope) {

    companion object {
        private const val TAG = "PeerManager"
    }

    private val peers = ConcurrentHashMap<String, MeshPeer>()

    private val _peerList = MutableStateFlow<List<MeshPeer>>(emptyList())
    val peerList: StateFlow<List<MeshPeer>> = _peerList.asStateFlow()

    private val _activePeerCount = MutableStateFlow(0)
    val activePeerCount: StateFlow<Int> = _activePeerCount.asStateFlow()

    private var cleanupJob: Job? = null

    /**
     * Start periodic cleanup of stale peers.
     */
    fun startCleanup() {
        cleanupJob?.cancel()
        cleanupJob = scope.launch {
            while (isActive) {
                delay(MeshConstants.PEER_CLEANUP_INTERVAL_MS)
                cleanupStalePeers()
            }
        }
    }

    /**
     * Add or update a peer. Returns true if this is a new peer.
     */
    fun addOrUpdatePeer(peerID: String, nickname: String): Boolean {
        val isNew = !peers.containsKey(peerID)
        peers[peerID] = peers[peerID]?.apply {
            this.nickname = nickname
            this.lastSeen = System.currentTimeMillis()
            this.isConnected = true
        } ?: MeshPeer(
            id = peerID,
            nickname = nickname,
            isConnected = true,
            lastSeen = System.currentTimeMillis()
        )

        if (isNew) {
            Log.d(TAG, "New peer discovered: $nickname ($peerID)")
        }
        refreshPeerList()
        return isNew
    }

    /**
     * Mark a peer as directly connected via BLE.
     */
    fun setDirectConnection(peerID: String, isDirect: Boolean) {
        peers[peerID]?.let {
            it.isDirectConnection = isDirect
            it.isConnected = isDirect
            it.lastSeen = System.currentTimeMillis()
            refreshPeerList()
        }
    }

    /**
     * Update RSSI for a peer.
     */
    fun updatePeerRSSI(peerID: String, rssi: Int) {
        peers[peerID]?.let {
            it.rssi = rssi
            it.lastSeen = System.currentTimeMillis()
        }
    }

    /**
     * Remove a peer from the registry.
     */
    fun removePeer(peerID: String) {
        peers.remove(peerID)
        Log.d(TAG, "Peer removed: $peerID")
        refreshPeerList()
    }

    /**
     * Mark a peer as disconnected.
     */
    fun markDisconnected(peerID: String) {
        peers[peerID]?.let {
            it.isConnected = false
            it.isDirectConnection = false
            refreshPeerList()
        }
    }

    /**
     * Get a specific peer by ID.
     */
    fun getPeer(peerID: String): MeshPeer? = peers[peerID]

    /**
     * Get nickname for a peer.
     */
    fun getPeerNickname(peerID: String): String? = peers[peerID]?.nickname

    /**
     * Get all peer nicknames as a map.
     */
    fun getAllPeerNicknames(): Map<String, String> {
        return peers.mapValues { it.value.nickname }
    }

    /**
     * Get active (connected) peer count.
     */
    fun getActivePeerCount(): Int = peers.values.count { it.isConnected }

    /**
     * Get all connected peer IDs.
     */
    fun getConnectedPeerIDs(): Set<String> {
        return peers.filter { it.value.isConnected }.keys
    }

    /**
     * Get all direct connection peer IDs.
     */
    fun getDirectPeerIDs(): Set<String> {
        return peers.filter { it.value.isDirectConnection }.keys
    }

    /**
     * Remove stale peers that haven't been seen recently.
     */
    private fun cleanupStalePeers() {
        val now = System.currentTimeMillis()
        val stale = peers.filter {
            !it.value.isDirectConnection &&
                    (now - it.value.lastSeen) > MeshConstants.STALE_PEER_TIMEOUT_MS
        }
        stale.keys.forEach { peerID ->
            Log.d(TAG, "Removing stale peer: $peerID")
            peers.remove(peerID)
        }
        if (stale.isNotEmpty()) {
            refreshPeerList()
        }
    }

    /**
     * Publish the current peer list to StateFlow observers.
     */
    fun refreshPeerList() {
        val sorted = peers.values.sortedByDescending { it.lastSeen }
        _peerList.value = sorted
        _activePeerCount.value = sorted.count { it.isConnected }
    }

    /**
     * Clear all peer data (for panic mode).
     */
    fun clearAllPeers() {
        peers.clear()
        refreshPeerList()
        Log.w(TAG, "All peers cleared")
    }

    /**
     * Stop cleanup job.
     */
    fun stop() {
        cleanupJob?.cancel()
    }
}
