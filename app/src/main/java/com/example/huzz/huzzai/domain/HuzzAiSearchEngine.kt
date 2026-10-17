package com.example.huzz.huzzai.domain

import android.content.Context
import android.util.Log
import com.example.huzz.huzzai.data.HuzzAiKnowledgeRepository
import com.example.huzz.huzzai.data.HuzzAiModelManager
import com.example.huzz.huzzai.model.HuzzAiCategory
import com.example.huzz.huzzai.model.HuzzAiChunk
import kotlinx.coroutines.flow.Flow
import com.example.huzz.huzzai.model.HuzzAiSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Main coordinator for query classification, manual retrieval, and on-device LLM synthesis.
 * [query] is retrieval only (instant, runs while typing); [answerStream] runs the LLM.
 */
class HuzzAiSearchEngine private constructor(context: Context) {

    companion object {
        private const val TAG = "HuzzAiSearchEngine"

        @Volatile
        private var INSTANCE: HuzzAiSearchEngine? = null

        fun getInstance(context: Context): HuzzAiSearchEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HuzzAiSearchEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val repository = HuzzAiKnowledgeRepository.getInstance(context)
    private val modelManager = HuzzAiModelManager.getInstance(context)
    private val batteryPolicy = HuzzAiBatteryPolicy(context)
    private val inferenceEngine = HuzzAiInferenceEngine(context, modelManager, batteryPolicy)

    suspend fun query(
        rawQuery: String,
        selectedCategory: HuzzAiCategory? = null
    ): HuzzAiSearchResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val trimmed = rawQuery.trim()

        // 1. Classify intent if category not manually selected
        val effectiveCategory = selectedCategory ?: HuzzAiQueryClassifier.classify(trimmed)

        // 2. Search manuals. Only a category the user picked is a hard filter;
        //    a guessed category must not hide the right manual.
        val matches = repository.search(trimmed, selectedCategory, limit = 5)
        val chunks = matches.map { it.first }
        val topSnippet = matches.firstOrNull()?.second
        val activeTier = modelManager.activeTier.value

        val latency = System.currentTimeMillis() - startTime
        Log.d(TAG, "Search completed for '$trimmed': ${chunks.size} chunks in ${latency}ms")

        return@withContext HuzzAiSearchResult(
            query = trimmed,
            category = effectiveCategory,
            localChunks = chunks,
            synthesizedResponse = null,
            matchedSnippet = topSnippet,
            latencyMs = latency,
            isFromModel = false,
            isFromMesh = false,
            activeModelTier = activeTier
        )
    }

    /** Streams an on-device LLM answer grounded in [chunks] (see [HuzzAiInferenceEngine]). */
    fun answerStream(query: String, chunks: List<HuzzAiChunk>): Flow<HuzzAiInferenceEngine.Event> =
        inferenceEngine.synthesizeStream(query.trim(), chunks, modelManager.activeTier.value)

    fun formatGuideAnswer(chunks: List<HuzzAiChunk>): String = inferenceEngine.formatGuideAnswer(chunks)

    suspend fun releaseModel() = inferenceEngine.releaseModel()

    suspend fun loadCategoryManuals(category: HuzzAiCategory?): HuzzAiSearchResult = withContext(Dispatchers.Default) {
        val chunks = repository.getChunksByCategory(category, limit = 10)
        return@withContext HuzzAiSearchResult(
            query = "",
            category = category,
            localChunks = chunks,
            synthesizedResponse = null,
            matchedSnippet = null,
            latencyMs = 0L,
            isFromModel = false,
            isFromMesh = false,
            activeModelTier = modelManager.activeTier.value
        )
    }
}
