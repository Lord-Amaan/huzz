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
        deleteRetiredModels()
        // Initialize existing file states
        refreshStates()
        val savedTierId = prefs.getString(KEY_ACTIVE_TIER, null)
        _activeTier.value = HuzzAiModelTier.entries.firstOrNull { it.tierId == savedTierId }
    }

    /** Removes model files from earlier app versions that no tier uses any more (can be 100s of MB). */
    private fun deleteRetiredModels() {
        val known = HuzzAiModelTier.entries.flatMap { listOf(it.fileName, "${it.fileName}.tmp") }.toSet()
        modelsDir.listFiles()?.filter { it.isFile && it.name !in known }?.forEach {
            Log.i(TAG, "Deleting retired model file ${it.name}")
            it.delete()
        }
    }

    fun refreshStates() {
        val states = mutableMapOf<String, ModelDownloadState>()
        for (tier in HuzzAiModelTier.entries) {
            val file = File(modelsDir, tier.fileName)
            if (isComplete(tier, file)) {
                states[tier.tierId] = ModelDownloadState.Ready(file.absolutePath, file.length())
            } else {
                states[tier.tierId] = ModelDownloadState.Idle
            }
        }
        _downloadStates.value = states
    }

    fun isModelReady(tier: HuzzAiModelTier): Boolean = isComplete(tier, File(modelsDir, tier.fileName))

    fun getModelFile(tier: HuzzAiModelTier): File? {
        val file = File(modelsDir, tier.fileName)
        return if (isComplete(tier, file)) file else null
    }

    /** A partially written model file would crash the native loader, so require the exact size. */
    private fun isComplete(tier: HuzzAiModelTier, file: File) =
        file.exists() && file.length() == tier.downloadSizeBytes

    /** Total device RAM, used to recommend a tier. */
    val totalRamMb: Long by lazy {
        val info = android.app.ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(info)
        info.totalMem / (1024 * 1024)
    }

    val recommendedTier: HuzzAiModelTier get() = HuzzAiModelTier.recommendedFor(totalRamMb)

    fun freeSpaceBytes(): Long = modelsDir.usableSpace

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
                // Resume a previous partial download if one exists.
                val resumeFrom = if (tempFile.exists() && tempFile.length() < tier.downloadSizeBytes) tempFile.length() else 0L
                if (resumeFrom == 0L) tempFile.delete()
                updateState(
                    tier.tierId,
                    ModelDownloadState.Downloading(
                        ((resumeFrom * 100) / tier.downloadSizeBytes).toInt(), resumeFrom, tier.downloadSizeBytes
                    )
                )

                val needed = tier.downloadSizeBytes - resumeFrom + 100_000_000L
                if (modelsDir.usableSpace < needed) {
                    throw IllegalStateException(
                        "Not enough storage: need ${needed / 1_000_000} MB free, have ${modelsDir.usableSpace / 1_000_000} MB"
                    )
                }

                val request = Request.Builder()
                    .url(tier.downloadUrl)
                    .apply { if (resumeFrom > 0) header("Range", "bytes=$resumeFrom-") }
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP ${response.code}: ${response.message}")
                    }

                    // 206 = server honoured the Range header; otherwise start over.
                    val appending = resumeFrom > 0 && response.code == 206
                    val body = response.body
                    val totalLength = tier.downloadSizeBytes

                    body.byteStream().use { input: InputStream ->
                        FileOutputStream(tempFile, appending).use { output: FileOutputStream ->
                            val buffer = ByteArray(64 * 1024)
                            var read: Int
                            var totalRead = if (appending) resumeFrom else 0L
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

                    if (tempFile.length() != tier.downloadSizeBytes) {
                        throw IllegalStateException(
                            "Download incomplete (${tempFile.length() / 1_000_000} of ${tier.downloadSizeBytes / 1_000_000} MB). Tap download to resume."
                        )
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
                updateState(tier.tierId, ModelDownloadState.Idle)
            } catch (e: Exception) {
                // Keep the partial file so the next attempt resumes where this one stopped.
                Log.e(TAG, "Error downloading model ${tier.tierId}", e)
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
