package com.example.huzz.huzzai.domain

import android.content.Context
import android.util.Log
import com.example.huzz.huzzai.model.HuzzAiModelTier
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.PromptTemplates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Holds at most one on-device model in memory (MediaPipe LLM Inference)
 * and streams generated text.
 */
class HuzzAiLlmRunner private constructor(private val context: Context) {

    companion object {
        private const val TAG = "HuzzAiLlmRunner"

        /** Input + output tokens; the bundled models use a 1280-token KV cache. */
        const val MAX_TOKENS = 1280
        private const val TOP_K = 40
        private const val TEMPERATURE = 0.2f

        private val NO_TEMPLATE: PromptTemplates = PromptTemplates.builder()
            .setUserPrefix("")
            .setUserSuffix("")
            .setModelPrefix("")
            .setModelSuffix("")
            .setSystemPrefix("")
            .setSystemSuffix("")
            .build()

        @Volatile
        private var INSTANCE: HuzzAiLlmRunner? = null

        fun getInstance(context: Context): HuzzAiLlmRunner {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HuzzAiLlmRunner(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val lock = Mutex()
    private var llm: LlmInference? = null

    @Volatile
    var loadedTier: HuzzAiModelTier? = null
        private set

    /** Loads [tier] from [file], replacing any other loaded model. Slow (seconds); call off the main thread. */
    suspend fun ensureLoaded(tier: HuzzAiModelTier, file: File) = withContext(Dispatchers.IO) {
        lock.withLock {
            if (loadedTier == tier && llm != null) return@withLock
            releaseLocked()
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(file.absolutePath)
                .setMaxTokens(MAX_TOKENS)
                .setMaxTopK(TOP_K)
                .setPreferredBackend(LlmInference.Backend.CPU)
                .build()
            llm = LlmInference.createFromOptions(context, options)
            loadedTier = tier
            Log.i(TAG, "Loaded ${tier.displayName}")
        }
    }

    /** Streams text pieces as the model generates them. Cancelling the collector stops generation. */
    fun generate(prompt: String): Flow<String> = callbackFlow {
        val engine = llm ?: throw IllegalStateException("No model loaded")
        val session = LlmInferenceSession.createFromOptions(
            engine,
            LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(TOP_K)
                .setTemperature(TEMPERATURE)
                // The caller formats the full chat prompt itself. Without explicit (empty)
                // templates, MediaPipe wraps it again with the bundle's template.
                .setPromptTemplates(NO_TEMPLATE)
                .build()
        )
        var finished = false
        session.addQueryChunk(prompt)
        val future = session.generateResponseAsync { partial, done ->
            if (!partial.isNullOrEmpty()) trySend(partial)
            if (done) {
                finished = true
                close()
            }
        }
        future.addListener({
            runCatching { future.get() }.onFailure { close(it) }
        }, Runnable::run)

        awaitClose {
            if (!finished) runCatching { session.cancelGenerateResponseAsync() }
            runCatching { session.close() }
        }
    }.flowOn(Dispatchers.IO)

    /** Frees the model's native memory. */
    suspend fun release() = lock.withLock { releaseLocked() }

    private fun releaseLocked() {
        runCatching { llm?.close() }
        llm = null
        loadedTier = null
    }
}
