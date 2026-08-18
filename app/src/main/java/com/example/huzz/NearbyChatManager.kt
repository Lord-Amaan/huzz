package com.example.huzz

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.nio.charset.StandardCharsets

/**
 * Data class representing a chat message.
 */
data class ChatMessage(
    val sender: String,
    val messageText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isFromMe: Boolean = false
)

/**
 * Enum representing the current connection state of the Nearby Connections client.
 */
enum class ConnectionStatus {
    Disconnected,
    Discovering,
    Advertising,
    Connected
}

/**
 * Manager class to handle Google Nearby Connections API for offline P2P chat.
 */
class NearbyChatManager(context: Context) {

    private val appContext = context.applicationContext
    private val connectionsClient = Nearby.getConnectionsClient(appContext)
    private val SERVICE_ID = "com.example.huzz.NEARBY_CHAT"
    private val STRATEGY = Strategy.P2P_STAR

    private var myName: String = "User"
    private val connectedEndpoints = mutableMapOf<String, String>() // Map of EndpointID to Display Name

    // StateFlows for UI observation
    private val _status = MutableStateFlow(ConnectionStatus.Disconnected)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<Map<String, String>>(emptyMap()) // EndpointID to Name
    val discoveredPeers: StateFlow<Map<String, String>> = _discoveredPeers.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    /**
     * Callback for receiving payloads (messages).
     */
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val bytes = payload.asBytes() ?: return
                val text = String(bytes, StandardCharsets.UTF_8)
                val senderName = connectedEndpoints[endpointId] ?: "Unknown"

                _messages.update { currentList ->
                    currentList + ChatMessage(
                        sender = senderName,
                        messageText = text,
                        isFromMe = false
                    )
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Not used in this simple implementation
        }
    }

    /**
     * Shared callback for connection lifecycle events.
     */
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d("NearbyChatManager", "Connection initiated with ${info.endpointName}")
            connectedEndpoints[endpointId] = info.endpointName
            // Automatically accept incoming connection requests
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                Log.d("NearbyChatManager", "Connected successfully to $endpointId")
                _status.value = ConnectionStatus.Connected
                // If we were discovering, we might want to stop, but P2P_STAR allows multiple
            } else {
                Log.d("NearbyChatManager", "Connection failed: ${result.status.statusMessage}")
                connectedEndpoints.remove(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d("NearbyChatManager", "Disconnected from $endpointId")
            connectedEndpoints.remove(endpointId)
            if (connectedEndpoints.isEmpty()) {
                _status.value = ConnectionStatus.Disconnected
            }
        }
    }

    /**
     * Starts advertising the device to nearby discoverers.
     */
    fun startAdvertising(username: String) {
        myName = username
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()

        connectionsClient.startAdvertising(
            username,
            SERVICE_ID,
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            Log.d("NearbyChatManager", "Advertising started as $username")
            _status.value = ConnectionStatus.Advertising
            _error.value = null
        }.addOnFailureListener { e ->
            Log.e("NearbyChatManager", "Advertising failed", e)
            _status.value = ConnectionStatus.Disconnected
            _error.value = "Advertising failed: ${e.message}"
        }
    }

    /**
     * Starts discovering nearby devices that are advertising.
     */
    fun startDiscovery() {
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()

        connectionsClient.startDiscovery(
            SERVICE_ID,
            object : EndpointDiscoveryCallback() {
                override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                    Log.d("NearbyChatManager", "Endpoint found: ${info.endpointName}")
                    _discoveredPeers.update { it + (endpointId to info.endpointName) }
                }

                override fun onEndpointLost(endpointId: String) {
                    Log.d("NearbyChatManager", "Endpoint lost: $endpointId")
                    _discoveredPeers.update { it - endpointId }
                }
            },
            options
        ).addOnSuccessListener {
            Log.d("NearbyChatManager", "Discovery started")
            _status.value = ConnectionStatus.Discovering
            _error.value = null
        }.addOnFailureListener { e ->
            Log.e("NearbyChatManager", "Discovery failed", e)
            _status.value = ConnectionStatus.Disconnected
            _error.value = "Discovery failed: ${e.message}"
        }
    }

    /**
     * Requests a connection to a specific discovered peer.
     */
    fun connectToPeer(endpointId: String, username: String) {
        myName = username
        connectionsClient.requestConnection(
            username,
            endpointId,
            connectionLifecycleCallback
        ).addOnFailureListener { e ->
            Log.e("NearbyChatManager", "Connection request failed", e)
        }
    }

    /**
     * Sends a string message to all connected endpoints.
     */
    fun sendMessage(text: String) {
        if (text.isBlank()) return
        
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        val payload = Payload.fromBytes(bytes)
        val endpointIds = connectedEndpoints.keys.toList()

        if (endpointIds.isNotEmpty()) {
            connectionsClient.sendPayload(endpointIds, payload)
            // Add my own message to the list
            _messages.update { currentList ->
                currentList + ChatMessage(
                    sender = myName,
                    messageText = text,
                    isFromMe = true
                )
            }
        } else {
            Log.w("NearbyChatManager", "No connected endpoints to send message to")
        }
    }

    /**
     * Stops all Nearby Connections activities (advertising, discovery, connections).
     */
    fun stopAll() {
        connectionsClient.stopAllEndpoints()
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectedEndpoints.clear()
        _discoveredPeers.value = emptyMap()
        _status.value = ConnectionStatus.Disconnected
        Log.d("NearbyChatManager", "All Nearby activities stopped")
    }
}
