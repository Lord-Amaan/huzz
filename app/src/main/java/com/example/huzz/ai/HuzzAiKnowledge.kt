package com.example.huzz.ai

/**
 * Data structures for offline Huzz AI emergency intelligence.
 */
data class EmergencyGuide(
    val id: String,
    val title: String,
    val category: GuideCategory,
    val steps: List<String>,
    val tags: List<String>
)

enum class GuideCategory {
    FIRST_AID,
    SURVIVAL,
    WATER_SANITATION,
    EMERGENCY_SIGNALS
}

object HuzzAiKnowledgeBase {

    val guides: List<EmergencyGuide> = listOf(
        EmergencyGuide(
            id = "cpr-adult",
            title = "Adult CPR Emergency Procedure",
            category = GuideCategory.FIRST_AID,
            steps = listOf(
                "Verify safety and check for responsiveness.",
                "Call for emergency help or signal nearby mesh peers.",
                "Place heel of hand on center of chest, other hand on top.",
                "Compress hard and fast at 100-120 BPM (2 inches depth).",
                "Allow full chest recoil between compressions.",
                "Deliver 2 rescue breaths after every 30 compressions if trained."
            ),
            tags = listOf("cpr", "heart", "resuscitation", "medical", "firstaid")
        ),
        EmergencyGuide(
            id = "water-purification",
            title = "Emergency Water Purification Methods",
            category = GuideCategory.WATER_SANITATION,
            steps = listOf(
                "Filter cloudy water through clean cloth or sand filter.",
                "Boiling: Bring to rolling boil for minimum 1 minute (3 mins above 2000m).",
                "Disinfection: Use water purification tablets or 2 drops unscented household bleach per liter.",
                "Let treated water stand for 30 minutes before consuming."
            ),
            tags = listOf("water", "purification", "boil", "sanitation", "survival")
        ),
        EmergencyGuide(
            id = "distress-sos",
            title = "Visual and Audio Distress Signals",
            category = GuideCategory.EMERGENCY_SIGNALS,
            steps = listOf(
                "Rule of Three: 3 whistle blasts, 3 fires, or 3 flashes of light.",
                "Pause for 1 minute, then repeat sequence.",
                "Signal mirror: Aim reflected flash towards rescue aircraft or search teams.",
                "Ground-to-air code: Form large 'V' (Require Assistance) or 'X' (Require Medical Assistance)."
            ),
            tags = listOf("sos", "distress", "signal", "rescue", "emergency")
        )
    )
}
