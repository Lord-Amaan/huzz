package com.example.huzz.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.huzz.mesh.ActiveSosAlert

/**
 * Compact, high-visibility emergency banner displayed persistently across chat views
 * when a local or remote distress beacon is active (FR-SOS-04, FR-SOS-05).
 */
@Composable
fun EmergencySosBanner(
    primaryAlert: ActiveSosAlert?,
    onCancelMySos: () -> Unit,
    onDismissRemoteAlert: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = primaryAlert != null,
        enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(250)) + fadeIn(animationSpec = tween(250)),
        exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(200)) + fadeOut(animationSpec = tween(150)),
        modifier = modifier
    ) {
        if (primaryAlert == null) return@AnimatedVisibility

        val isSelf = primaryAlert.senderNickname == "You" || primaryAlert.senderPeerID == "self"

        // Pulsing glow animation for distress indicator
        val infiniteTransition = rememberInfiniteTransition(label = "sos_pulse")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_alpha"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = 1.dp,
                    color = com.example.huzz.ui.theme.NothingRed.copy(alpha = pulseAlpha),
                    shape = RoundedCornerShape(8.dp)
                )
                .background(com.example.huzz.ui.theme.NothingSurface)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Main Row: Beacon Dot + Title + Status Badge + Battery + Action Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left cluster: Beacon + Title + Status + Telemetry
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        // Pulsing red beacon dot (Nothing signature red)
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(com.example.huzz.ui.theme.NothingRed.copy(alpha = pulseAlpha)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(3.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                        }

                        // Title in Space Mono
                        Text(
                            text = if (isSelf) "SOS ACTIVE" else "SOS: ${primaryAlert.senderNickname.uppercase()}",
                            fontFamily = com.example.huzz.ui.theme.SpaceMonoFamily,
                            color = com.example.huzz.ui.theme.NothingRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Status Badge in technical bracket
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .border(1.dp, com.example.huzz.ui.theme.NothingRed.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .background(com.example.huzz.ui.theme.NothingBlack)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "[${primaryAlert.payload.status.displayName.uppercase()}]",
                                fontFamily = com.example.huzz.ui.theme.SpaceMonoFamily,
                                color = com.example.huzz.ui.theme.NothingTextPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        // Battery pill
                        if (primaryAlert.payload.batteryPct >= 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .border(1.dp, Color(0xFF222222), RoundedCornerShape(4.dp))
                                    .background(com.example.huzz.ui.theme.NothingBlack)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "BAT:${primaryAlert.payload.batteryPct}%",
                                    fontFamily = com.example.huzz.ui.theme.SpaceMonoFamily,
                                    color = if (primaryAlert.payload.batteryPct <= 15) com.example.huzz.ui.theme.NothingRed else com.example.huzz.ui.theme.NothingTextSecondary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Geohash pill
                        if (!primaryAlert.payload.geohash.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .border(1.dp, Color(0xFF222222), RoundedCornerShape(4.dp))
                                    .background(com.example.huzz.ui.theme.NothingBlack)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "#${primaryAlert.payload.geohash}",
                                    fontFamily = com.example.huzz.ui.theme.SpaceMonoFamily,
                                    color = com.example.huzz.ui.theme.NothingTextSecondary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Right action: "I AM SAFE" technical button or dismiss
                    if (isSelf) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .border(1.dp, com.example.huzz.ui.theme.NothingStatusGreen, RoundedCornerShape(4.dp))
                                .background(com.example.huzz.ui.theme.NothingBlack)
                                .clickable { onCancelMySos() }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "[I AM SAFE]",
                                fontFamily = com.example.huzz.ui.theme.SpaceMonoFamily,
                                color = com.example.huzz.ui.theme.NothingStatusGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { onDismissRemoteAlert(primaryAlert.senderPeerID) },
                            modifier = Modifier
                                .size(24.dp)
                                .padding(start = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Dismiss",
                                tint = com.example.huzz.ui.theme.NothingTextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                // Note subline (only shown if user entered non-blank details)
                if (primaryAlert.payload.note.isNotBlank()) {
                    Text(
                        text = "// ${primaryAlert.payload.note}",
                        fontFamily = com.example.huzz.ui.theme.SpaceMonoFamily,
                        color = com.example.huzz.ui.theme.NothingTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 14.dp)
                    )
                }
            }
        }
    }
}
