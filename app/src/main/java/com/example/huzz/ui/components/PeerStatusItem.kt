package com.example.huzz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.huzz.mesh.MeshUtils

/**
 * UI Component for displaying an active mesh peer with signal quality indicator.
 */
@Composable
fun PeerStatusItem(
    peerId: String,
    nickname: String,
    rssi: Int,
    isDirectConnection: Boolean,
    modifier: Modifier = Modifier
) {
    val signal = MeshUtils.getSignalStrength(rssi)
    val signalColor = when (signal) {
        MeshUtils.SignalQuality.EXCELLENT -> Color(0xFF4CAF50)
        MeshUtils.SignalQuality.GOOD -> Color(0xFF8BC34A)
        MeshUtils.SignalQuality.FAIR -> Color(0xFFFFC107)
        MeshUtils.SignalQuality.POOR -> Color(0xFFFF5722)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = nickname.take(1).uppercase(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = nickname,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "ID: ${MeshUtils.formatDisplayPeerId(peerId)} • ${if (isDirectConnection) "Direct" else "Relayed"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(signalColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${rssi}dBm",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
