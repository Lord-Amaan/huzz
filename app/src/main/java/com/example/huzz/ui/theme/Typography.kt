package com.example.huzz.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Base font size for consistent scaling across the app
internal const val BASE_FONT_SIZE = com.example.huzz.util.AppConstants.UI.BASE_FONT_SIZE_SP

/**
 * Message body style. The generous leading (1.4x) is what gives the redesigned chat surface
 * its readable rhythm; without an explicit lineHeight, Compose falls back to the font's own
 * metrics and lines sit too tightly for long paragraphs.
 */
val MessageBodyTextStyle = ChatVisualTokens.MessageBodyStyle

/** Sender label above a message group. Single line, never wraps. */
val MessageSenderTextStyle = ChatVisualTokens.SenderStyle

// Typography matching the iOS monospace design - using BASE_FONT_SIZE for consistency
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = DotoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 56.sp,
        letterSpacing = (-1.0).sp
    ),
    displayMedium = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp
    ),
    displaySmall = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = (BASE_FONT_SIZE + 13).sp,
        lineHeight = (BASE_FONT_SIZE + 21).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = (BASE_FONT_SIZE + 7).sp,
        lineHeight = (BASE_FONT_SIZE + 15).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Medium,
        fontSize = (BASE_FONT_SIZE + 3).sp,
        lineHeight = (BASE_FONT_SIZE + 9).sp
    ),
    titleLarge = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Medium,
        fontSize = (BASE_FONT_SIZE + 5).sp,
        lineHeight = (BASE_FONT_SIZE + 13).sp
    ),
    titleMedium = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Medium,
        fontSize = (BASE_FONT_SIZE + 1).sp,
        lineHeight = (BASE_FONT_SIZE + 7).sp
    ),
    bodyLarge = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Normal,
        fontSize = (BASE_FONT_SIZE + 1).sp,
        lineHeight = (BASE_FONT_SIZE + 7).sp
    ),
    bodyMedium = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Normal,
        fontSize = BASE_FONT_SIZE.sp,
        lineHeight = (BASE_FONT_SIZE + 6).sp
    ),
    bodySmall = TextStyle(
        fontFamily = SpaceMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = (BASE_FONT_SIZE - 2).sp,
        lineHeight = (BASE_FONT_SIZE + 2).sp,
        letterSpacing = 0.3.sp
    ),
    labelLarge = TextStyle(
        fontFamily = SpaceMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = (BASE_FONT_SIZE - 1).sp,
        lineHeight = (BASE_FONT_SIZE + 5).sp,
        letterSpacing = 0.5.sp
    ),
    labelMedium = TextStyle(
        fontFamily = SpaceMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = (BASE_FONT_SIZE - 2).sp,
        lineHeight = (BASE_FONT_SIZE + 3).sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = SpaceMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = (BASE_FONT_SIZE - 4).sp,
        lineHeight = (BASE_FONT_SIZE + 1).sp,
        letterSpacing = 0.5.sp
    )
)
