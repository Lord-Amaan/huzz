package com.example.huzz.huzzai.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.huzz.huzzai.model.HuzzAiCategory
import com.example.huzz.huzzai.model.HuzzAiChunk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

/**
 * Repository responsible for loading pre-bundled offline manuals into SQLite FTS5
 * and executing fast offline queries.
 */
class HuzzAiKnowledgeRepository private constructor(private val context: Context) {

    companion object {
        private const val TAG = "HuzzAiKnowledgeRepo"

        @Volatile
        private var INSTANCE: HuzzAiKnowledgeRepository? = null

        fun getInstance(context: Context): HuzzAiKnowledgeRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HuzzAiKnowledgeRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val dbHelper = HuzzAiDatabaseHelper(context)
    private val gson = Gson()
    private var isInitialized = false

    /**
     * Initializes repository, loading bundled JSON manuals from assets on first run.
     */
    suspend fun initializeIfNeeded() = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext

        try {
            val count = dbHelper.getChunkCount()
            if (count == 0) {
                Log.i(TAG, "Database empty. Ingesting pre-bundled emergency manuals...")
                loadBundledAssetManuals()
            } else {
                Log.d(TAG, "Knowledge base ready with $count pre-indexed emergency manual chunks.")
            }
            isInitialized = true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing HuzzAi Knowledge Base", e)
        }
    }

    private fun loadBundledAssetManuals() {
        val assetFiles = listOf(
            "huzzai/first_aid_cpr.json",
            "huzzai/water_sanitation.json",
            "huzzai/disaster_survival.json",
            "huzzai/huzz_extended_guide.json"
        )

        val listType = object : TypeToken<List<HuzzAiChunk>>() {}.type

        for (fileName in assetFiles) {
            try {
                context.assets.open(fileName).use { inputStream ->
                    InputStreamReader(inputStream).use { reader ->
                        val chunks: List<HuzzAiChunk> = gson.fromJson(reader, listType)
                        for (chunk in chunks) {
                            dbHelper.insertOrUpdateChunk(chunk)
                        }
                        Log.i(TAG, "Indexed ${chunks.size} chunks from $fileName")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not load asset file: $fileName", e)
            }
        }
    }

    /**
     * Search manual chunks using native FTS5 BM25.
     */
    suspend fun search(
        query: String,
        categoryFilter: HuzzAiCategory? = null,
        limit: Int = 5
    ): List<Pair<HuzzAiChunk, String?>> = withContext(Dispatchers.IO) {
        initializeIfNeeded()
        return@withContext dbHelper.searchChunks(query, categoryFilter, limit)
    }

    /**
     * Retrieve all chunks under a category.
     */
    suspend fun getChunksByCategory(category: HuzzAiCategory?, limit: Int = 10): List<HuzzAiChunk> = withContext(Dispatchers.IO) {
        initializeIfNeeded()
        return@withContext dbHelper.getAllByCategory(category, limit)
    }
}
