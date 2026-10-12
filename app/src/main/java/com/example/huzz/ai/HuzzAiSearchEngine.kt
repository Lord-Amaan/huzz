package com.example.huzz.ai

/**
 * Fast offline search and retrieval engine for Huzz AI emergency guides.
 */
object HuzzAiSearchEngine {

    /**
     * Search guides by query string across title, steps, and tags.
     */
    fun search(query: String): List<EmergencyGuide> {
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isEmpty()) return emptyList()

        val tokens = cleanQuery.split("\\s+".toRegex()).filter { it.isNotBlank() }

        return HuzzAiKnowledgeBase.guides.mapNotNull { guide ->
            val score = calculateMatchScore(guide, tokens)
            if (score > 0) Pair(guide, score) else null
        }.sortedByDescending { it.second }
         .map { it.first }
    }

    /**
     * Filters guides by emergency category.
     */
    fun getByCategory(category: GuideCategory): List<EmergencyGuide> {
        return HuzzAiKnowledgeBase.guides.filter { it.category == category }
    }

    private fun calculateMatchScore(guide: EmergencyGuide, tokens: List<String>): Int {
        var score = 0
        val titleLower = guide.title.lowercase()
        val tagsLower = guide.tags.map { it.lowercase() }
        val stepsLower = guide.steps.joinToString(" ").lowercase()

        for (token in tokens) {
            if (titleLower.contains(token)) score += 5
            if (tagsLower.contains(token)) score += 3
            if (stepsLower.contains(token)) score += 1
        }
        return score
    }
}
