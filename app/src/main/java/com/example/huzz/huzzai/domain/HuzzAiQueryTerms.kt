package com.example.huzz.huzzai.domain

import java.util.Locale
import kotlin.math.ceil

/**
 * Turns a natural-language emergency question into search terms:
 * drops filler words, stems lightly, and adds synonyms so everyday wording
 * ("my friend got bitten by a snake") reaches the manual vocabulary.
 */
object HuzzAiQueryTerms {

    /** One group per meaningful query word: the word's stem followed by its synonyms. */
    fun groups(query: String): List<List<String>> =
        query.lowercase(Locale.US)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 2 && it !in STOPWORDS }
            .distinct()
            .map { word -> (listOf(stem(word)) + SYNONYMS[word].orEmpty()).distinct() }

    /**
     * Minimum number of groups a manual must match. Prevents one incidental word
     * (e.g. "best", "make") from producing a confident but wrong answer.
     */
    fun minMatched(groupCount: Int): Int = ceil(groupCount * 0.4).toInt().coerceAtLeast(1)

    private fun stem(word: String): String {
        for (suffix in listOf("ing", "ed", "es", "s")) {
            if (word.length > suffix.length + 3 && word.endsWith(suffix)) return word.dropLast(suffix.length)
        }
        return word
    }

    private val STOPWORDS = setOf(
        "a", "an", "the", "is", "are", "was", "were", "be", "been", "to", "of", "in", "on",
        "at", "for", "and", "or", "if", "it", "its", "my", "me", "i", "you", "your", "we",
        "our", "they", "them", "he", "she", "his", "her", "this", "that", "these", "those",
        "what", "how", "do", "does", "did", "can", "could", "should", "would", "will", "with",
        "from", "by", "as", "so", "but", "not", "no", "about", "into", "there", "then",
        "than", "when", "where", "who", "which", "why", "any", "some", "get", "got", "have",
        "has", "had", "just", "now", "please", "help", "someone", "person", "people",
        "am", "im", "us", "need", "know", "tell", "want", "best", "way", "thing", "go", "going",
        "friend", "really", "very", "much"
    )

    // Values are substrings matched against manual text, so stems are fine ("bleed" matches "bleeding").
    private val SYNONYMS = mapOf(
        "quake" to listOf("earthquake"),
        "tremor" to listOf("earthquake"),
        "shaking" to listOf("earthquake"),
        "drink" to listOf("water"),
        "thirsty" to listOf("water", "dehydrat"),
        "purify" to listOf("water", "boil", "disinfect"),
        "blood" to listOf("bleed", "hemorrhag", "tourniquet"),
        "bleeding" to listOf("hemorrhag", "tourniquet"),
        "bleed" to listOf("hemorrhag", "tourniquet"),
        "cut" to listOf("bleed", "wound"),
        "heart" to listOf("cpr"),
        "unconscious" to listOf("recovery", "cpr", "breath"),
        "fainted" to listOf("recovery", "unconscious"),
        "breathe" to listOf("cpr", "chok"),
        "broken" to listOf("fracture"),
        "hurricane" to listOf("cyclone"),
        "typhoon" to listOf("cyclone"),
        "storm" to listOf("cyclone", "lightning"),
        "smoke" to listOf("fire"),
        "burnt" to listOf("burn"),
        "scald" to listOf("burn"),
        "trapped" to listOf("rubble", "trap"),
        "stuck" to listOf("trap", "rubble"),
        "buried" to listOf("rubble", "trap"),
        "collapsed" to listOf("rubble", "earthquake"),
        "diarrhea" to listOf("diarrhoea", "ors"),
        "vomiting" to listOf("dehydrat", "ors"),
        "hot" to listOf("heat"),
        "cold" to listOf("hypothermia"),
        "freezing" to listOf("hypothermia"),
        "snake" to listOf("snakebite"),
        "bitten" to listOf("bite"),
        "dog" to listOf("animal", "rabies"),
        "electricity" to listOf("electric"),
        "fit" to listOf("seizure"),
        "convulsion" to listOf("seizure"),
        "anxious" to listOf("panic", "calm"),
        "scared" to listOf("panic", "calm"),
        "rescue" to listOf("signal"),
        "lost" to listOf("signal", "rescue"),
        "sos" to listOf("signal"),
        "kit" to listOf("emergency", "bag"),
        "pack" to listOf("kit", "bag"),
        "battery" to listOf("phone"),
        "generator" to listOf("carbon monoxide"),
        "inside" to listOf("indoor"),
        "mud" to listOf("landslide"),
        "wave" to listOf("tsunami"),
        "baby" to listOf("child", "infant"),
        "kid" to listOf("child"),
        "pregnant" to listOf("childbirth"),
        "labour" to listOf("childbirth"),
        "labor" to listOf("childbirth"),
        "toilet" to listOf("sanitation", "latrine"),
        "fridge" to listOf("food", "refrigerator"),
        "ambulance" to listOf("emergency number")
    )
}
