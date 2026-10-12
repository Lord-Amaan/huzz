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

/**
 * Specifications for on-demand downloadable on-device Small Language Models.
 */
enum class HuzzAiModelTier(
    val tierId: String,
    val displayName: String,
    val description: String,
    val parameterSize: String,
    val downloadSizeBytes: Long,
    val ramRequirementMb: Int,
    val downloadUrl: String,
    val fileName: String
) {
    TIER_1_LIGHTWEIGHT(
        tierId = "smollm2_360m_q4",
        displayName = "Ultra-Lightweight (SmolLM2)",
        description = "Optimized for budget devices and emergency low-power states.",
        parameterSize = "360M Params (4-bit)",
        downloadSizeBytes = 195_000_000L, // ~195 MB
        ramRequirementMb = 350,
        downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF/resolve/main/smollm2-360m-instruct-q4_k_m.gguf",
        fileName = "smollm2_360m_q4.bin"
    ),
    TIER_2_BALANCED(
        tierId = "qwen25_05b_q4",
        displayName = "Balanced Guidance (Qwen2.5)",
        description = "Excellent reasoning and step-by-step instruction synthesis for mid-range phones.",
        parameterSize = "0.5B Params (4-bit)",
        downloadSizeBytes = 380_000_000L, // ~380 MB
        ramRequirementMb = 700,
        downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
        fileName = "qwen25_05b_q4.bin"
    ),
    TIER_3_PRECISION(
        tierId = "gemma2_2b_q4",
        displayName = "High Precision (Gemma-2)",
        description = "Deep medical & situational comprehension for flagship hardware.",
        parameterSize = "2B Params (4-bit)",
        downloadSizeBytes = 1_150_000_000L, // ~1.15 GB
        ramRequirementMb = 1800,
        downloadUrl = "https://huggingface.co/google/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
        fileName = "gemma2_2b_q4.bin"
    )
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
