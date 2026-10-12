package com.example.huzz.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Top-bar Emergency SOS button featuring:
 * - 3-second press-and-hold activation with progressive circular fill (FR-SOS-01)
 * - Progressive haptic ticking (ticks every 500ms while holding)
 * - Heavy haptic bump upon reaching the 3-second threshold
 * - Active state indicator if an SOS broadcast is currently live
 */
@Composable
fun EmergencySosButton(
    isSosActive: Boolean,
    onHoldComplete: () -> Unit,
    onActiveSosClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPressed by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableFloatStateOf(0f) }

    // Pulsing transition for active SOS state
    val infiniteTransition = rememberInfiniteTransition(label = "sos_btn_active")
    val activeGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "btn_glow"
    )

    // Helper for progressive haptic tick
    fun performHapticTick() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(30)
            }
        } catch (_: Exception) {}
    }

    // Helper for 3s completion heavy vibration
    fun performHeavyCompletionVibration() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 50, 150), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(200)
            }
        } catch (_: Exception) {}
    }

    // Coroutine managing the 3-second hold timer and progressive ticks
    LaunchedEffect(isPressed) {
        if (isPressed) {
            val totalDurationMs = 3000L
            val tickIntervalMs = 500L
            val startTime = System.currentTimeMillis()
            var nextTickTime = startTime + tickIntervalMs

            while (isActive && isPressed) {
                val elapsed = System.currentTimeMillis() - startTime
                holdProgress = (elapsed.toFloat() / totalDurationMs).coerceIn(0f, 1f)

                if (System.currentTimeMillis() >= nextTickTime && elapsed < totalDurationMs) {
                    performHapticTick()
                    nextTickTime += tickIntervalMs
                }

                if (elapsed >= totalDurationMs) {
                    performHeavyCompletionVibration()
                    holdProgress = 1f
                    break
                }
                delay(25)
            }
        } else {
            holdProgress = 0f
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(36.dp)
            .pointerInput(isSosActive) {
                if (isSosActive) {
                    detectTapGestures(
                        onTap = { onActiveSosClick() }
                    )
                } else {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            val startTime = System.currentTimeMillis()
                            val released = tryAwaitRelease()
                            val heldTime = System.currentTimeMillis() - startTime
                            isPressed = false
                            if (released && heldTime >= 2900L) {
                                onHoldComplete()
                            }
                        }
                    )
                }
            }
    ) {
        // Progressive charge ring indicator
        if (isPressed && holdProgress > 0f) {
            CircularProgressIndicator(
                progress = { holdProgress },
                modifier = Modifier.size(36.dp),
                color = com.example.huzz.ui.theme.NothingRed,
                trackColor = com.example.huzz.ui.theme.NothingBorder,
                strokeWidth = 2.dp,
                strokeCap = StrokeCap.Square
            )
        }

        // Inner circular button surface
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isSosActive -> com.example.huzz.ui.theme.NothingRed.copy(alpha = activeGlowAlpha)
                        isPressed -> com.example.huzz.ui.theme.NothingRed
                        else -> Color(0xFF141414)
                    }
                )
                .border(
                    width = 1.dp,
                    color = when {
                        isSosActive || isPressed -> com.example.huzz.ui.theme.NothingRed
                        else -> Color(0xFF2A2A2A)
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "SOS",
                fontFamily = com.example.huzz.ui.theme.SpaceMonoFamily,
                color = if (isSosActive || isPressed) Color.White else com.example.huzz.ui.theme.NothingTextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}
