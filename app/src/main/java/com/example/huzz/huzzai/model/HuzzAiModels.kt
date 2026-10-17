package com.example.huzz.huzzai.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Emergency categories supported by the HuzzAi Knowledge & Guidance engine.
 */
enum class HuzzAiCategory(val displayName: String, val iconName: String) {
    FIRST_AID("First Aid", "medical_services"),
    WATER_SANITATION("Water & Hygiene", "water_drop"),
    TRAUMA_CPR("CPR & Trauma", "favorite"),
    DISASTER_PREP("Disaster Triage", "warning"),
    SHELTER_FIRE("Shelter & Fire", "local_fire_department"),
    SIGNALING("Emergency Signaling", "sos");

    companion object {
        fun fromString(value: String): HuzzAiCategory {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: FIRST_AID
        }
    }
}

/**
 * Core unit of verified offline knowledge.
 */
@Parcelize
data class HuzzAiChunk(
    val id: String,
    val title: String,
    val category: HuzzAiCategory,
    val summary: String,
    val contentMarkdown: String,
    val steps: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val sourceManual: String
) : Parcelable

/** How a model expects its chat prompt to be formatted. */
enum class HuzzAiPromptFormat { CHATML, PHI }

/**
 * On-device LLMs run with MediaPipe LLM Inference (.task bundles from the
 * litert-community Hugging Face org; openly licensed, no login required).
 * Lite is the default for low-end phones. Without any model, HuzzAi shows
 * the matched manual's steps instead.
 */
enum class HuzzAiModelTier(
    val tierId: String,
    val displayName: String,
    val description: String,
    val parameterSize: String,
    val downloadSizeBytes: Long,
    val ramRequirementMb: Int,
    val downloadUrl: String,
    val fileName: String,
    val promptFormat: HuzzAiPromptFormat,
    /** Character budget for retrieved context; these bundles use a 1280-token KV cache. */
    val contextChars: Int
) {
    // Smaller models (e.g. SmolLM 135M) were tried and produce unusable, looping output.
    LITE(
        tierId = "qwen25_05b_q8",
        displayName = "Lite (Qwen2.5 0.5B)",
        description = "Default for most phones. Smallest model that follows instructions reliably.",
        parameterSize = "0.5B Params (8-bit)",
        downloadSizeBytes = 546_660_344L,
        ramRequirementMb = 1_200,
        downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        fileName = "qwen25_05b_q8.task",
        promptFormat = HuzzAiPromptFormat.CHATML,
        contextChars = 1800
    ),
    STANDARD(
        tierId = "qwen25_15b_q8",
        displayName = "Standard (Qwen2.5 1.5B)",
        description = "Clearer, more reliable step-by-step answers for mid-range phones.",
        parameterSize = "1.5B Params (8-bit)",
        downloadSizeBytes = 1_597_913_616L,
        ramRequirementMb = 3_000,
        downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        fileName = "qwen25_15b_q8.task",
        promptFormat = HuzzAiPromptFormat.CHATML,
        contextChars = 2200
    ),
    PRO(
        tierId = "phi4_mini_q8",
        displayName = "Pro (Phi-4 mini 3.8B)",
        description = "Best comprehension for flagship phones with 8 GB+ RAM. Slowest.",
        parameterSize = "3.8B Params (8-bit)",
        downloadSizeBytes = 3_944_275_882L,
        ramRequirementMb = 5_500,
        downloadUrl = "https://huggingface.co/litert-community/Phi-4-mini-instruct/resolve/main/Phi-4-mini-instruct_multi-prefill-seq_q8_ekv1280.task",
        fileName = "phi4_mini_q8.task",
        promptFormat = HuzzAiPromptFormat.PHI,
        contextChars = 2200
    );

    /** Text that marks the end of the model's turn; generation is cut there. */
    val stopMarkers: List<String>
        get() = when (promptFormat) {
            HuzzAiPromptFormat.CHATML -> listOf("<|im_end|>", "<|im_start|>", "<|endoftext|>")
            HuzzAiPromptFormat.PHI -> listOf("<|end|>", "<|user|>", "<|system|>", "<|assistant|>", "<|endoftext|>")
        }

    fun formatPrompt(system: String, user: String): String = when (promptFormat) {
        HuzzAiPromptFormat.CHATML ->
            "<|im_start|>system\n$system<|im_end|>\n<|im_start|>user\n$user<|im_end|>\n<|im_start|>assistant\n"
        HuzzAiPromptFormat.PHI ->
            "<|system|>$system<|end|><|user|>$user<|end|><|assistant|>"
    }

    companion object {
        /** Largest tier whose RAM need fits comfortably in this device's total RAM. */
        fun recommendedFor(totalRamMb: Long): HuzzAiModelTier =
            entries.lastOrNull { it.ramRequirementMb * 2 <= totalRamMb } ?: LITE
    }
}

/**
 * State of model download in the Model Hub.
 */
sealed class ModelDownloadState {
    data object Idle : ModelDownloadState()
    data class Downloading(val progressPercent: Int, val bytesDownloaded: Long, val totalBytes: Long) : ModelDownloadState()
    data class Ready(val localFilePath: String, val fileSizeBytes: Long) : ModelDownloadState()
    data class Error(val message: String) : ModelDownloadState()
}

/**
 * Result returned by the HuzzAi search & synthesis pipeline.
 */
data class HuzzAiSearchResult(
    val query: String,
    val category: HuzzAiCategory?,
    val localChunks: List<HuzzAiChunk>,
    val synthesizedResponse: String? = null,
    val matchedSnippet: String? = null,
    val latencyMs: Long = 0L,
    val isFromModel: Boolean = false,
    val isFromMesh: Boolean = false,
    val activeModelTier: HuzzAiModelTier? = null
)

/**
 * Payload broadcast across the mesh when local search has zero hits.
 */
data class HuzzAiMeshQuery(
    val queryId: String,
    val senderPeerId: String,
    val queryText: String,
    val category: HuzzAiCategory,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Response returned by a peer containing verified manual guidance.
 */
data class HuzzAiMeshResponse(
    val queryId: String,
    val responderPeerId: String,
    val responderNickname: String,
    val matchedTitle: String,
    val guidanceSnippet: String,
    val steps: List<String>,
    val timestamp: Long = System.currentTimeMillis()
)
