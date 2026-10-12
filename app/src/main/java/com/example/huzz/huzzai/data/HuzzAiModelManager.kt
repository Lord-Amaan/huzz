package com.example.huzz.huzzai.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.huzz.huzzai.model.HuzzAiModelTier
import com.example.huzz.huzzai.model.ModelDownloadState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Manages downloading, storage, verification, and lifecycle of on-device SLM models.
 * Model files are saved in the app's sandboxed noBackupFilesDir to prevent cloud backup bloat
 * and ensure zero-permission security.
 */
class HuzzAiModelManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "HuzzAiModelManager"
        private const val PREFS_NAME = "huzzai_model_prefs"
        private const val KEY_ACTIVE_TIER = "active_model_tier"

        @Volatile
        private var INSTANCE: HuzzAiModelManager? = null

        fun getInstance(context: Context): HuzzAiModelManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HuzzAiModelManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val modelsDir: File = File(context.noBackupFilesDir, "huzzai/models").apply { mkdirs() }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeDownloadJobs = ConcurrentHashMap<String, Job>()

    private val _downloadStates = MutableStateFlow<Map<String, ModelDownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, ModelDownloadState>> = _downloadStates.asStateFlow()

    private val _activeTier = MutableStateFlow<HuzzAiModelTier?>(null)
    val activeTier: StateFlow<HuzzAiModelTier?> = _activeTier.asStateFlow()

    init {
        // Initialize existing file states
        refreshStates()
        val savedTierId = prefs.getString(KEY_ACTIVE_TIER, null)
        _activeTier.value = HuzzAiModelTier.entries.firstOrNull { it.tierId == savedTierId }
    }

    fun refreshStates() {
        val states = mutableMapOf<String, ModelDownloadState>()
        for (tier in HuzzAiModelTier.entries) {
            val file = File(modelsDir, tier.fileName)
            if (file.exists() && file.length() > 0) {
                states[tier.tierId] = ModelDownloadState.Ready(file.absolutePath, file.length())
            } else {
                states[tier.tierId] = ModelDownloadState.Idle
            }
        }
        _downloadStates.value = states
    }

    fun isModelReady(tier: HuzzAiModelTier): Boolean {
        val file = File(modelsDir, tier.fileName)
        return file.exists() && file.length() > 0
    }

    fun getModelFile(tier: HuzzAiModelTier): File? {
        val file = File(modelsDir, tier.fileName)
        return if (file.exists() && file.length() > 0) file else null
    }

    fun setActiveTier(tier: HuzzAiModelTier?) {
        _activeTier.value = tier
        prefs.edit().putString(KEY_ACTIVE_TIER, tier?.tierId).apply()
        Log.i(TAG, "Active HuzzAi model tier set to: ${tier?.displayName ?: "None (Pure FTS5)"}")
    }

    /**
     * Download model over Wi-Fi with streaming progress tracking.
     */
    fun startDownload(tier: HuzzAiModelTier) {
        if (activeDownloadJobs.containsKey(tier.tierId)) {
            Log.w(TAG, "Download already in progress for tier ${tier.tierId}")
            return
        }

        val job = coroutineScope.launch {
            val targetFile = File(modelsDir, tier.fileName)
            val tempFile = File(modelsDir, "${tier.fileName}.tmp")

            try {
                updateState(tier.tierId, ModelDownloadState.Downloading(0, 0, tier.downloadSizeBytes))

                val request = Request.Builder()
                    .url(tier.downloadUrl)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP ${response.code}: ${response.message}")
                    }

                    val body = response.body ?: throw IllegalStateException("Empty response body")
                    val totalLength = if (body.contentLength() > 0) body.contentLength() else tier.downloadSizeBytes

                    body.byteStream().use { input: InputStream ->
                        FileOutputStream(tempFile).use { output: FileOutputStream ->
                            val buffer = ByteArray(64 * 1024)
                            var read: Int
                            var totalRead = 0L
                            var lastProgressTime = 0L

                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                totalRead += read

                                val now = System.currentTimeMillis()
                                if (now - lastProgressTime > 200 || totalRead == totalLength) {
                                    val percent = ((totalRead * 100) / totalLength).toInt().coerceIn(0, 100)
                                    updateState(
                                        tier.tierId,
                                        ModelDownloadState.Downloading(percent, totalRead, totalLength)
                                    )
                                    lastProgressTime = now
                                }
                            }
                            output.flush()
                        }
                    }

                    // Rename temp to target
                    if (tempFile.exists()) {
                        if (targetFile.exists()) targetFile.delete()
                        tempFile.renameTo(targetFile)
                    }

                    Log.i(TAG, "Model download completed for ${tier.displayName}: ${targetFile.length()} bytes")
                    updateState(tier.tierId, ModelDownloadState.Ready(targetFile.absolutePath, targetFile.length()))

                    // If no active tier set, auto-select newly downloaded tier
                    if (_activeTier.value == null) {
                        setActiveTier(tier)
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Download cancelled for ${tier.tierId}")
                tempFile.delete()
                updateState(tier.tierId, ModelDownloadState.Idle)
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading model ${tier.tierId}", e)
                tempFile.delete()
                updateState(tier.tierId, ModelDownloadState.Error(e.localizedMessage ?: "Download failed"))
            } finally {
                activeDownloadJobs.remove(tier.tierId)
            }
        }

        activeDownloadJobs[tier.tierId] = job
    }

    fun cancelDownload(tier: HuzzAiModelTier) {
        activeDownloadJobs[tier.tierId]?.cancel()
        activeDownloadJobs.remove(tier.tierId)
        val tempFile = File(modelsDir, "${tier.fileName}.tmp")
        if (tempFile.exists()) tempFile.delete()
        updateState(tier.tierId, ModelDownloadState.Idle)
    }

    /**
     * Delete model file to immediately reclaim disk space.
     */
    fun deleteModel(tier: HuzzAiModelTier) {
        cancelDownload(tier)
        val file = File(modelsDir, tier.fileName)
        if (file.exists()) {
            file.delete()
        }
        if (_activeTier.value == tier) {
            setActiveTier(null)
        }
        updateState(tier.tierId, ModelDownloadState.Idle)
        Log.i(TAG, "Model file deleted for tier ${tier.tierId}")
    }

    private fun updateState(tierId: String, state: ModelDownloadState) {
        val current = _downloadStates.value.toMutableMap()
        current[tierId] = state
        _downloadStates.value = current
    }
}
