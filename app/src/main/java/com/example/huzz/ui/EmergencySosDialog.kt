package com.example.huzz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.huzz.model.EmergencyStatus

/**
 * Dialog opened upon releasing the 3-second SOS press-and-hold trigger (FR-SOS-01).
 * Displays telemetry (battery %, geohash) and lets the user choose an emergency
 * status chip and optional note before flooding the mesh.
 */
@Composable
fun EmergencySosDialog(
    show: Boolean,
    batteryPct: Int,
    geohash: String?,
    onDismiss: () -> Unit,
    onConfirmBroadcast: (EmergencyStatus, String) -> Unit
) {
    if (!show) return

    var selectedStatus by remember { mutableStateOf(EmergencyStatus.MEDICAL) }
    var noteText by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .border(1.dp, com.example.huzz.ui.theme.NothingRed, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            color = com.example.huzz.ui.theme.NothingSurface,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header: Beacon Mark + Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, com.example.huzz.ui.theme.NothingRed, RoundedCornerShape(6.dp))
                            .background(com.example.huzz.ui.theme.NothingBlack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = com.example.huzz.ui.theme.NothingRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "EMERGENCY DISTRESS",
                            fontFamily = com.example.huzz.ui.theme.SpaceMonoFamily,
                            color = com.example.huzz.ui.theme.NothingTextDisplay,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "// FLOODS ALL MESH NODES (TTL: 7)",
                            fontFamily = com.example.huzz.ui.theme.SpaceMonoFamily,
                            color = com.example.huzz.ui.theme.NothingTextSecondary,
                            fontSize = 10.sp,
                            letterSpacing = 0.3.sp
                        )
                    }
                }

                // Telemetry summary card
                Surface(
                    color = com.example.huzz.ui.theme.NothingBlack,
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, com.example.huzz.ui.theme.NothingBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Battery
                        Text(
                            text = "BAT: ${if (batteryPct >= 0) "$batteryPct%" else "N/A"}",
                            fontFamily = com.example.huzz.ui.theme.SpaceMonoFamily,
                            color = if (batteryPct <= 15) com.example.huzz.ui.theme.NothingRed else com.example.huzz.ui.theme.NothingTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )

                        // Geohash
                        Text(
                            text = "LOC: ${geohash ?: "UNAVAILABLE"}",
                            fontFamily = com.example.huzz.ui.theme.SpaceMonoFamily,
                            color = com.example.huzz.ui.theme.NothingTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Emergency Status Category Selector
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "[CATEGORY]",
                        fontFamily = com.example.huzz.ui.theme.SpaceMonoFamily,
                        color = com.example.huzz.ui.theme.NothingTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    val statusOptions = listOf(
                        Triple(EmergencyStatus.MEDICAL, "Medical", Icons.Filled.MedicalServices),
                        Triple(EmergencyStatus.TRAPPED, "Trapped", Icons.Filled.Lock),
                        Triple(EmergencyStatus.EVACUATING, "Evacuating", Icons.Filled.DirectionsRun),
                        Triple(EmergencyStatus.ASSISTANCE, "Assistance", Icons.Filled.Handshake)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        statusOptions.take(2).forEach { (status, label, icon) ->
                            StatusOptionChip(
                                label = label,
                                icon = icon,
                                isSelected = selectedStatus == status,
                                onClick = { selectedStatus = status },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        statusOptions.drop(2).forEach { (status, label, icon) ->
                            StatusOptionChip(
                                label = label,
                                icon = icon,
                                isSelected = selectedStatus == status,
                                onClick = { selectedStatus = status },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Optional note input
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "[DETAILS (OPTIONAL)]",
                        fontFamily = com.example.huzz.ui.theme.SpaceMonoFamily,
                        color = com.example.huzz.ui.theme.NothingTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { if (it.length <= 120) noteText = it },
                        placeholder = {
                            Text(
                                "// e.g. 2 injured, trapped under debris...",
                                fontFamily = com.example.huzz.ui.theme.SpaceMonoFamily,
                                color = com.example.huzz.ui.theme.NothingTextTertiary,
                                fontSize = 12.sp
                            )
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = com.example.huzz.ui.theme.SpaceGroteskFamily,
                            color = com.example.huzz.ui.theme.NothingTextPrimary,
                            fontSize = 13.sp
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = com.example.huzz.ui.theme.NothingRed,
                            unfocusedBorderColor = com.example.huzz.ui.theme.NothingBorder,
                            focusedTextColor = com.example.huzz.ui.theme.NothingTextPrimary,
                            unfocusedTextColor = com.example.huzz.ui.theme.NothingTextPrimary,
                            cursorColor = com.example.huzz.ui.theme.NothingRed,
                            focusedContainerColor = com.example.huzz.ui.theme.NothingBlack,
                            unfocusedContainerColor = com.example.huzz.ui.theme.NothingBlack
                        ),
                        shape = RoundedCornerShape(6.dp),
                        maxLines = 3
                    )
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, com.example.huzz.ui.theme.NothingBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = com.example.huzz.ui.theme.NothingTextSecondary
                        )
                    ) {
                        Text(
                            text = "[CANCEL]",
                            fontFamily = com.example.huzz.ui.theme.SpaceMonoFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = {
                            onConfirmBroadcast(selectedStatus, noteText.trim())
                            onDismiss()
                        },
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = com.example.huzz.ui.theme.NothingRed,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "[TRANSMIT SOS]",
                            fontFamily = com.example.huzz.ui.theme.SpaceMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusOptionChip(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = if (isSelected) com.example.huzz.ui.theme.NothingRed else com.example.huzz.ui.theme.NothingBorder,
                shape = RoundedCornerShape(6.dp)
            ),
        color = if (isSelected) Color(0xFF1C0D0D) else com.example.huzz.ui.theme.NothingBlack,
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) com.example.huzz.ui.theme.NothingRed else com.example.huzz.ui.theme.NothingTextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label.uppercase(),
                fontFamily = com.example.huzz.ui.theme.SpaceMonoFamily,
                color = if (isSelected) Color.White else com.example.huzz.ui.theme.NothingTextPrimary,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                letterSpacing = 0.5.sp
            )
        }
    }
}
