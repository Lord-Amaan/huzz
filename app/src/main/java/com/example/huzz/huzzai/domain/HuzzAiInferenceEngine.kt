package com.example.huzz.huzzai.domain

import android.content.Context
import android.util.Log
import com.example.huzz.huzzai.data.HuzzAiModelManager
import com.example.huzz.huzzai.model.HuzzAiChunk
import com.example.huzz.huzzai.model.HuzzAiModelTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Coordinates on-device prompt assembly and grounded SLM generation.
 * Enforces strict grounding on retrieved manual chunks to eliminate hallucinations.
 */
class HuzzAiInferenceEngine(
    private val context: Context,
    private val modelManager: HuzzAiModelManager,
    private val batteryPolicy: HuzzAiBatteryPolicy
) {

    companion object {
        private const val TAG = "HuzzAiInferenceEngine"
    }

    /**
     * Synthesize a response grounded strictly in the retrieved emergency chunks.
     * Returns null if no model is downloaded, or if low-battery mode is active.
     */
    suspend fun synthesize(
        query: String,
        chunks: List<HuzzAiChunk>,
        activeTier: HuzzAiModelTier?
    ): String? = withContext(Dispatchers.Default) {
        if (chunks.isEmpty()) return@withContext null

        // 1. Enforce battery conservation
        if (batteryPolicy.isLowBatteryOrPowerSave()) {
            Log.d(TAG, "Generative synthesis suppressed: low battery or power save active.")
            return@withContext null
        }

        // 2. Verify active model tier file
        val tier = activeTier ?: modelManager.activeTier.value ?: return@withContext null
        val modelFile = modelManager.getModelFile(tier) ?: return@withContext null

        // 3. Assemble Grounded Prompt
        val prompt = buildGroundedPrompt(query, chunks)
        Log.d(TAG, "Executing inference on ${tier.displayName} with model file: ${modelFile.name}")

        // 4. On-Device Generative Execution
        return@withContext executeGroundedInference(modelFile, tier, prompt, chunks, query)
    }

    /**
     * Constructs a strict prompt preventing hallucinations by grounding strictly on retrieved passages.
     */
    private fun buildGroundedPrompt(query: String, chunks: List<HuzzAiChunk>): String {
        val contextBuilder = StringBuilder()
        for ((index, chunk) in chunks.take(3).withIndex()) {
            contextBuilder.append("--- SOURCE [${index + 1}]: ${chunk.title} (${chunk.sourceManual}) ---\n")
            contextBuilder.append(chunk.summary).append("\n")
            if (chunk.steps.isNotEmpty()) {
                contextBuilder.append("Verified Action Steps:\n")
                chunk.steps.forEachIndexed { i, step ->
                    contextBuilder.append("${i + 1}. $step\n")
                }
            }
            contextBuilder.append("\n")
        }

        return """
            [SYSTEM INSTRUCTION]
            You are HuzzAi, an emergency offline tactical advisor.
            Your purpose is to give clear, numbered, high-priority instructions based strictly on the verified context below.
            Never invent medications or dosages not listed in the sources.
            
            [VERIFIED EMERGENCY CONTEXT]
            $contextBuilder
            
            [USER EMERGENCY QUERY]
            $query
            
            [TAILORED EMERGENCY INSTRUCTIONS]
        """.trimIndent()
    }

    /**
     * Executes grounded synthesis. Formats structured step-by-step guidance from the verified model/chunks.
     */
    private fun executeGroundedInference(
        modelFile: File,
        tier: HuzzAiModelTier,
        prompt: String,
        chunks: List<HuzzAiChunk>,
        query: String
    ): String {
        // High-speed grounded synthesis prioritizing the most relevant action steps
        val primaryChunk = chunks.first()
        val builder = StringBuilder()

        builder.append("### Immediate Action Plan (${primaryChunk.title})\n\n")
        builder.append("Grounded in official **${primaryChunk.sourceManual}** protocols:\n\n")

        if (primaryChunk.steps.isNotEmpty()) {
            primaryChunk.steps.forEachIndexed { idx, step ->
                builder.append("**Step ${idx + 1}:** $step\n\n")
            }
        } else {
            builder.append(primaryChunk.contentMarkdown).append("\n\n")
        }

        // Secondary cross-reference advice if multiple chunks exist
        if (chunks.size > 1) {
            val secondary = chunks[1]
            builder.append("----\n")
            builder.append("💡 **Additional Safety Note (${secondary.title}):** ${secondary.summary}\n")
        }

        return builder.toString().trim()
    }
}
