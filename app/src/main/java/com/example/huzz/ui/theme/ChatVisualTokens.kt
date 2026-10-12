package com.example.huzz.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.huzz.R

/**
 * Nothing-inspired font families:
 * - Space Grotesk: Primary interface text, headers, message bodies
 * - Space Mono: Technical metadata, timestamps, badges, ALL CAPS labels
 * - Doto: Dot-matrix display and emergency status moments
 */
val SpaceGroteskFamily = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.SemiBold),
    Font(R.font.space_grotesk, FontWeight.Bold),
)

val SpaceMonoFamily = FontFamily(
    Font(R.font.space_mono_regular, FontWeight.Normal),
    Font(R.font.space_mono_bold, FontWeight.Bold),
)

val DotoFamily = FontFamily(
    Font(R.font.doto, FontWeight.Normal),
    Font(R.font.doto, FontWeight.Bold),
)

/** The primary font family used throughout the app (Space Grotesk). */
internal val HuzzFontFamily = SpaceGroteskFamily

/** Exact typography, spacing, and opacity values exported for the chat transcript. */
internal object ChatVisualTokens {
    val MessageBodyFontSize: TextUnit = 14.sp
    val MessageBodyLineHeight: TextUnit = 20.sp
    val SenderFontSize: TextUnit = 14.sp
    val SenderLineHeight: TextUnit = 16.sp
    val SystemActionFontSize: TextUnit = 12.sp
    val SystemActionLineHeight: TextUnit = 16.sp
    val SystemTimeFontSize: TextUnit = 10.sp

    val MessageItemSpacing: Dp = 8.dp
    val SenderTopPadding: Dp = 8.dp
    val SenderToBodySpacing: Dp = 4.dp

    // MARK: - Bubble geometry (ChatUiMode.Bubbles)

    /** Rounded corner on the three "free" corners of a message bubble (Nothing 8-12px standard). */
    val BubbleCornerRadius: Dp = 10.dp

    /** Tightened corner on the speaker's own side, giving the bubble a subtle tail. */
    val BubbleTailRadius: Dp = 4.dp

    /** Padding inside a bubble, around the text. */
    val BubblePaddingHorizontal: Dp = 12.dp
    val BubblePaddingVertical: Dp = 8.dp

    /** A bubble never grows past this fraction of the list width, so long lines still wrap. */
    const val BubbleMaxWidthFraction: Float = 0.80f

    /**
     * Author-colour wash inside a bubble. Matches the mention-chip treatment so a tinted
     * bubble stays legible on both the near-black and near-white chat surfaces.
     */
    const val BubbleBackgroundAlpha: Float = 0.18f

    /** Author-colour hairline around a bubble; stronger than the fill so the shape reads. */
    const val BubbleBorderAlpha: Float = 0.38f

    const val SenderSuffixAlpha: Float = 0.60f
    const val HighlightAlpha: Float = 0.20f
    const val MutedTextAlpha: Float = 0.50f

    val MessageBodyStyle = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Normal,
        fontSize = MessageBodyFontSize,
        lineHeight = MessageBodyLineHeight,
    )

    val SenderStyle = TextStyle(
        fontFamily = SpaceMonoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = SenderFontSize,
        lineHeight = SenderLineHeight,
        letterSpacing = 0.5.sp,
    )

    val SystemActionStyle = TextStyle(
        fontFamily = SpaceMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = SystemActionFontSize,
        lineHeight = SystemActionLineHeight,
        letterSpacing = 0.5.sp,
    )
}
