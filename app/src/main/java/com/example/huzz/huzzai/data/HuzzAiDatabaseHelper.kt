package com.example.huzz.huzzai.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.huzz.huzzai.model.HuzzAiCategory
import com.example.huzz.huzzai.model.HuzzAiChunk

/**
 * Native SQLite helper managing the HuzzAi offline knowledge base.
 * Employs standard universal SQLite tables with intelligent relevance scoring
 * to guarantee 100% crash-free offline search across all Android devices.
 */
class HuzzAiDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    companion object {
        private const val TAG = "HuzzAiDatabaseHelper"
        private const val DATABASE_NAME = "huzzai_knowledge.db"
        private const val DATABASE_VERSION = 2

        const val TABLE_CHUNKS = "huzzai_chunks"

        const val COL_ID = "id"
        const val COL_TITLE = "title"
        const val COL_CATEGORY = "category"
        const val COL_SUMMARY = "summary"
        const val COL_CONTENT = "content"
        const val COL_STEPS = "steps_json"
        const val COL_TAGS = "tags_json"
        const val COL_SOURCE = "source"
    }

    private val gson = Gson()

    override fun onCreate(db: SQLiteDatabase) {
        try {
            val createChunksTable = """
                CREATE TABLE IF NOT EXISTS $TABLE_CHUNKS (
                    $COL_ID TEXT PRIMARY KEY,
                    $COL_TITLE TEXT NOT NULL,
                    $COL_CATEGORY TEXT NOT NULL,
                    $COL_SUMMARY TEXT NOT NULL,
                    $COL_CONTENT TEXT NOT NULL,
                    $COL_STEPS TEXT,
                    $COL_TAGS TEXT,
                    $COL_SOURCE TEXT NOT NULL
                );
            """.trimIndent()
            db.execSQL(createChunksTable)

            // Create index on category for fast filtering
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_chunks_category ON $TABLE_CHUNKS ($COL_CATEGORY);")
            Log.d(TAG, "HuzzAi database tables initialized successfully.")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed creating HuzzAi database tables", t)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        try { db.execSQL("DROP TABLE IF EXISTS huzzai_chunks_fts") } catch (_: Throwable) { }
        try { db.execSQL("DROP TABLE IF EXISTS $TABLE_CHUNKS") } catch (_: Throwable) { }
        onCreate(db)
    }

    /**
     * Insert or update a HuzzAi manual chunk.
     */
    fun insertOrUpdateChunk(chunk: HuzzAiChunk) {
        val db = try { writableDatabase } catch (t: Throwable) {
            Log.e(TAG, "Cannot get writableDatabase", t)
            return
        }
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put(COL_ID, chunk.id)
                put(COL_TITLE, chunk.title)
                put(COL_CATEGORY, chunk.category.name)
                put(COL_SUMMARY, chunk.summary)
                put(COL_CONTENT, chunk.contentMarkdown)
                put(COL_STEPS, gson.toJson(chunk.steps))
                put(COL_TAGS, gson.toJson(chunk.tags))
                put(COL_SOURCE, chunk.sourceManual)
            }
            db.insertWithOnConflict(TABLE_CHUNKS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.e(TAG, "Failed inserting chunk: ${chunk.id}", e)
        } finally {
            try { db.endTransaction() } catch (_: Throwable) { }
        }
    }

    /**
     * Retrieve chunk count in database.
     */
    fun getChunkCount(): Int {
        val db = try { readableDatabase } catch (t: Throwable) { return 0 }
        return try {
            val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_CHUNKS", null)
            var count = 0
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0)
            }
            cursor.close()
            count
        } catch (t: Throwable) {
            Log.e(TAG, "Failed getting chunk count", t)
            0
        }
    }

    /**
     * Query chunks using weighted relevance scoring (Title=10, Tags=7, Summary=4, Content=1).
     */
    fun searchChunks(sanitizedQuery: String, categoryFilter: HuzzAiCategory? = null, limit: Int = 5): List<Pair<HuzzAiChunk, String?>> {
        if (sanitizedQuery.isBlank()) {
            return getAllByCategory(categoryFilter, limit).map { it to null }
        }

        val all = getAllByCategory(categoryFilter, 100)
        val terms = sanitizedQuery.trim().lowercase().split("\\s+".toRegex()).filter { it.length >= 2 }
        if (terms.isEmpty()) {
            return all.take(limit).map { it to null }
        }

        val scored = all.mapNotNull { chunk ->
            var score = 0
            val titleLower = chunk.title.lowercase()
            val summaryLower = chunk.summary.lowercase()
            val contentLower = chunk.contentMarkdown.lowercase()
            val tagsLower = chunk.tags.joinToString(" ").lowercase()
            var matchedSnippet: String? = null

            for (term in terms) {
                if (titleLower.contains(term)) score += 10
                if (tagsLower.contains(term)) score += 7
                if (summaryLower.contains(term)) {
                    score += 4
                    if (matchedSnippet == null) matchedSnippet = extractSnippet(chunk.summary, term)
                }
                if (contentLower.contains(term)) {
                    score += 1
                    if (matchedSnippet == null) matchedSnippet = extractSnippet(chunk.contentMarkdown, term)
                }
            }

            if (score > 0) {
                (chunk to (matchedSnippet ?: chunk.summary)) to score
            } else {
                null
            }
        }.sortedByDescending { it.second }
        .map { it.first }
        .take(limit)

        return scored
    }

    private fun extractSnippet(text: String, term: String): String {
        val idx = text.indexOf(term, ignoreCase = true)
        if (idx == -1) return text.take(120)
        val start = (idx - 30).coerceAtLeast(0)
        val end = (idx + term.length + 60).coerceAtMost(text.length)
        val prefix = if (start > 0) "..." else ""
        val suffix = if (end < text.length) "..." else ""
        return prefix + text.substring(start, end).replace("\n", " ").trim() + suffix
    }

    /**
     * Get chunks by category if query is blank.
     */
    fun getAllByCategory(categoryFilter: HuzzAiCategory? = null, limit: Int = 10): List<HuzzAiChunk> {
        val results = mutableListOf<HuzzAiChunk>()
        val db = try { readableDatabase } catch (t: Throwable) { return emptyList() }
        val selection = if (categoryFilter != null) "$COL_CATEGORY = ?" else null
        val selectionArgs = if (categoryFilter != null) arrayOf(categoryFilter.name) else null

        try {
            val cursor = db.query(
                TABLE_CHUNKS,
                null,
                selection,
                selectionArgs,
                null,
                null,
                "$COL_TITLE ASC",
                limit.toString()
            )

            val stringListType = object : TypeToken<List<String>>() {}.type
            while (cursor.moveToNext()) {
                val id = cursor.getString(cursor.getColumnIndexOrThrow(COL_ID))
                val title = cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE))
                val categoryStr = cursor.getString(cursor.getColumnIndexOrThrow(COL_CATEGORY))
                val summary = cursor.getString(cursor.getColumnIndexOrThrow(COL_SUMMARY))
                val content = cursor.getString(cursor.getColumnIndexOrThrow(COL_CONTENT))
                val stepsJson = cursor.getString(cursor.getColumnIndexOrThrow(COL_STEPS))
                val tagsJson = cursor.getString(cursor.getColumnIndexOrThrow(COL_TAGS))
                val source = cursor.getString(cursor.getColumnIndexOrThrow(COL_SOURCE))

                val steps: List<String> = try { gson.fromJson(stepsJson, stringListType) ?: emptyList() } catch (_: Exception) { emptyList() }
                val tags: List<String> = try { gson.fromJson(tagsJson, stringListType) ?: emptyList() } catch (_: Exception) { emptyList() }

                results.add(
                    HuzzAiChunk(
                        id = id,
                        title = title,
                        category = HuzzAiCategory.fromString(categoryStr),
                        summary = summary,
                        contentMarkdown = content,
                        steps = steps,
                        tags = tags,
                        sourceManual = source
                    )
                )
            }
            cursor.close()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed reading chunks from database", t)
        }
        return results
    }
}
