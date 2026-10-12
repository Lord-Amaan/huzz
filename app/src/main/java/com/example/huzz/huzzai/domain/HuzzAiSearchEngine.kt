package com.example.huzz.huzzai.domain

import android.content.Context
import android.util.Log
import com.example.huzz.huzzai.data.HuzzAiKnowledgeRepository
import com.example.huzz.huzzai.data.HuzzAiModelManager
import com.example.huzz.huzzai.model.HuzzAiCategory
import com.example.huzz.huzzai.model.HuzzAiSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Main coordinator for query classification, FTS5 retrieval, and SLM synthesis.
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

        // 2. Perform FTS5 BM25 search
        val matches = repository.search(trimmed, effectiveCategory, limit = 5)
        val chunks = matches.map { it.first }
        val topSnippet = matches.firstOrNull()?.second

        // 3. Attempt SLM synthesis if model is active and battery permits
        val activeTier = modelManager.activeTier.value
        val synthesized = if (chunks.isNotEmpty() && activeTier != null) {
            inferenceEngine.synthesize(trimmed, chunks, activeTier)
        } else {
            null
        }

        val latency = System.currentTimeMillis() - startTime
        Log.d(TAG, "Search completed for '$trimmed': ${chunks.size} chunks, synth=${synthesized != null} in ${latency}ms")

        return@withContext HuzzAiSearchResult(
            query = trimmed,
            category = effectiveCategory,
            localChunks = chunks,
            synthesizedResponse = synthesized,
            matchedSnippet = topSnippet,
            latencyMs = latency,
            isFromModel = synthesized != null,
            isFromMesh = false,
            activeModelTier = activeTier
        )
    }

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
