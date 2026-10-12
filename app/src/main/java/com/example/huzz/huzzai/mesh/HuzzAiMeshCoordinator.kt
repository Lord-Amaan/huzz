package com.example.huzz.huzzai.mesh

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.example.huzz.huzzai.model.HuzzAiCategory
import com.example.huzz.huzzai.model.HuzzAiMeshQuery
import com.example.huzz.huzzai.model.HuzzAiMeshResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Handles P2P Emergency Knowledge sharing across the local BLE mesh.
 * Broadcasts emergency query requests when a device has zero local hits.
 */
class HuzzAiMeshCoordinator private constructor(private val context: Context) {

    companion object {
        private const val TAG = "HuzzAiMeshCoordinator"
        const val LANTERN_BROADCAST_PREFIX = "[LANTERN_QUERY]"
        const val LANTERN_RESPONSE_PREFIX = "[LANTERN_RESP]"

        @Volatile
        private var INSTANCE: HuzzAiMeshCoordinator? = null

        fun getInstance(context: Context): HuzzAiMeshCoordinator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HuzzAiMeshCoordinator(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val gson = Gson()
    private val _meshResponses = MutableStateFlow<List<HuzzAiMeshResponse>>(emptyList())
    val meshResponses: StateFlow<List<HuzzAiMeshResponse>> = _meshResponses.asStateFlow()

    private val _isQueryingMesh = MutableStateFlow(false)
    val isQueryingMesh: StateFlow<Boolean> = _isQueryingMesh.asStateFlow()

    /**
     * Broadcast an emergency knowledge query to nearby mesh peers.
     */
    fun createQueryBroadcastMessage(queryText: String, category: HuzzAiCategory, myPeerId: String): String {
        _isQueryingMesh.value = true
        val query = HuzzAiMeshQuery(
            queryId = UUID.randomUUID().toString().take(8),
            senderPeerId = myPeerId,
            queryText = queryText,
            category = category
        )
        return "$LANTERN_BROADCAST_PREFIX ${gson.toJson(query)}"
    }

    /**
     * Process potential incoming HuzzAi packet from mesh chat stream.
     */
    fun processIncomingMessage(content: String, senderPeerId: String, senderNickname: String): Boolean {
        if (content.startsWith(LANTERN_RESPONSE_PREFIX)) {
            try {
                val json = content.removePrefix(LANTERN_RESPONSE_PREFIX).trim()
                val response = gson.fromJson(json, HuzzAiMeshResponse::class.java)
                val current = _meshResponses.value.toMutableList()
                current.add(0, response)
                _meshResponses.value = current
                _isQueryingMesh.value = false
                Log.i(TAG, "Received verified HuzzAi response from ${response.responderNickname}: ${response.matchedTitle}")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Failed parsing HuzzAi mesh response", e)
            }
        }
        return false
    }

    fun clearResponses() {
        _meshResponses.value = emptyList()
        _isQueryingMesh.value = false
    }
}
