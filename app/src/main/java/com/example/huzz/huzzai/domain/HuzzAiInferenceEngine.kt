package com.example.huzz.huzzai.domain

import android.content.Context
import android.util.Log
import com.example.huzz.huzzai.data.HuzzAiModelManager
import com.example.huzz.huzzai.model.HuzzAiChunk
import com.example.huzz.huzzai.model.HuzzAiModelTier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile

/**
 * Coordinates on-device prompt assembly and grounded LLM generation.
 * The model only sees the retrieved manual chunks and is told to answer from them alone.
 * Output is cut at end-of-turn markers and checked before it is shown as the final answer.
 */
class HuzzAiInferenceEngine(
    private val context: Context,
    private val modelManager: HuzzAiModelManager,
    private val batteryPolicy: HuzzAiBatteryPolicy
) {

    companion object {
        private const val TAG = "HuzzAiInferenceEngine"
        private const val MAX_ANSWER_CHARS = 1500

        private const val SYSTEM_PROMPT =
            "You are HuzzAi, an offline emergency and disaster assistant. " +
            "Answer using ONLY the verified manual excerpts provided. " +
            "Reply with short, clear numbered steps. " +
            "Never invent medications or dosages. " +
            "If the excerpts do not answer the question, say you don't know. " +
            "If a life is in danger, first tell the user to contact emergency services or nearby people."

        /** A marker that is only partly generated so far, e.g. "<|im_" at the end. */
        private val PARTIAL_MARKER = Regex("<\\|?[a-z_]*$")
        private val CODE_WORDS = Regex("\\b(def|import|python|print)\\b")
        private val WORD = Regex("[a-z]{5,}")
    }

    sealed interface Event {
        /** Model is being loaded into memory (first question after opening HuzzAi). */
        data class LoadingModel(val tier: HuzzAiModelTier) : Event
        /** The answer so far, already cut at stop markers. */
        data class Partial(val text: String) : Event
        /** Final answer. [fromModel] is false when we fell back to the formatted manual. */
        data class Done(val text: String, val fromModel: Boolean, val note: String? = null) : Event
    }

    private val runner = HuzzAiLlmRunner.getInstance(context)

    /**
     * Streams a grounded answer for [query]. Falls back to the formatted top manual
     * when there is no model, battery is low, the model fails, or its answer looks unreliable.
     */
    fun synthesizeStream(
        query: String,
        chunks: List<HuzzAiChunk>,
        activeTier: HuzzAiModelTier?
    ): Flow<Event> = flow {
        if (chunks.isEmpty()) return@flow

        val tier = activeTier ?: modelManager.activeTier.value
        val modelFile = tier?.let { modelManager.getModelFile(it) }
        if (tier == null || modelFile == null) {
            emit(Event.Done(formatGuideAnswer(chunks), fromModel = false))
            return@flow
        }

        // Enforce battery conservation
        if (batteryPolicy.isLowBatteryOrPowerSave()) {
            Log.d(TAG, "Generative synthesis suppressed: low battery or power save active.")
            emit(Event.Done(formatGuideAnswer(chunks), fromModel = false, note = "Battery saver: showing manual steps instead of AI"))
            return@flow
        }

        val raw = StringBuilder()
        var answer = ""
        var looping = false
        val userMessage = buildUserMessage(query, chunks, tier)
        try {
            if (runner.loadedTier != tier) emit(Event.LoadingModel(tier))
            runner.ensureLoaded(tier, modelFile)
            val prompt = tier.formatPrompt(SYSTEM_PROMPT, userMessage)
            var stop = false
            // takeWhile cancels generation as soon as a guard trips.
            runner.generate(prompt).takeWhile { !stop }.collect { piece ->
                raw.append(piece)
                val (text, endOfTurn) = cutAtStopMarker(raw.toString(), tier)
                answer = text.trim()
                looping = isLooping(answer)
                if (endOfTurn || looping || answer.length > MAX_ANSWER_CHARS) stop = true
                emit(Event.Partial(answer))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "On-device generation failed for ${tier.displayName}", t)
            runner.release()
            emit(Event.Done(formatGuideAnswer(chunks), fromModel = false, note = "AI model failed; showing manual steps. Try a smaller model."))
            return@flow
        }

        if (looping || !isGroundedAnswer(answer, userMessage)) {
            Log.w(TAG, "Discarding unreliable answer (looping=$looping): ${answer.take(160)}")
            emit(
                Event.Done(
                    formatGuideAnswer(chunks),
                    fromModel = false,
                    note = "The AI answer looked unreliable, so here are the manual steps instead."
                )
            )
            return@flow
        }
        emit(Event.Done(answer, fromModel = true))
    }

    /** Frees the model's memory, e.g. when HuzzAi is closed. */
    suspend fun releaseModel() = runner.release()

    /** Text before the first end-of-turn marker, and whether a marker was found. */
    private fun cutAtStopMarker(text: String, tier: HuzzAiModelTier): Pair<String, Boolean> {
        val cut = tier.stopMarkers.map { text.indexOf(it) }.filter { it >= 0 }.minOrNull()
        val visible = if (cut != null) text.substring(0, cut) else text.replace(PARTIAL_MARKER, "")
        return visible to (cut != null)
    }

    /** True when the model is stuck repeating itself (e.g. "apple, banana, orange, apple, ..."). */
    private fun isLooping(text: String): Boolean {
        if (text.length < 120) return false
        val probe = text.takeLast(24)
        return text.takeLast(400).windowed(probe.length).count { it == probe } >= 4
    }

    /**
     * Rejects answers that aren't about the manuals: very short output, code, or
     * text that shares almost no meaningful words with the retrieved excerpts.
     */
    private fun isGroundedAnswer(answer: String, context: String): Boolean {
        if (answer.length < 20) return false
        val lower = answer.lowercase()
        if ("```" in lower || CODE_WORDS.containsMatchIn(lower)) return false
        fun words(t: String) = WORD.findAll(t.lowercase()).map { it.value }.toSet()
        return (words(answer) intersect words(context)).size >= 3
    }

    /**
     * Compact context that fits the 1280-token window. The Lite model gets the top
     * two matches (the best manual is sometimes #2) within its smaller budget.
     */
    private fun buildUserMessage(query: String, chunks: List<HuzzAiChunk>, tier: HuzzAiModelTier): String {
        val used = if (tier == HuzzAiModelTier.LITE) chunks.take(2) else chunks.take(3)
        var budget = tier.contextChars
        val context = StringBuilder()
        used.forEachIndexed { index, chunk ->
            if (budget <= 0) return@forEachIndexed
            val body = buildString {
                append(chunk.summary)
                if (chunk.steps.isNotEmpty()) {
                    append(" Steps: ")
                    chunk.steps.forEachIndexed { i, step -> append("${i + 1}) $step ") }
                }
            }.take(budget)
            budget -= body.length
            context.append("[${index + 1}] ${chunk.title}: ").append(body.trim()).append("\n\n")
        }
        return "Manual excerpts:\n$context" + "Question: $query"
    }

    /** Readable answer built straight from the best manual (no model needed). */
    fun formatGuideAnswer(chunks: List<HuzzAiChunk>): String {
        val primaryChunk = chunks.first()
        val builder = StringBuilder()

        builder.append("${primaryChunk.title}\n")
        builder.append("From ${primaryChunk.sourceManual}\n\n")

        if (primaryChunk.steps.isNotEmpty()) {
            primaryChunk.steps.forEachIndexed { idx, step ->
                builder.append("${idx + 1}. $step\n")
            }
        } else {
            builder.append(primaryChunk.summary).append("\n")
        }

        // Secondary cross-reference advice if multiple chunks exist
        if (chunks.size > 1) {
            val secondary = chunks[1]
            builder.append("\nAlso see: ${secondary.title}. ${secondary.summary}\n")
        }

        return builder.toString().trim()
    }
}
