package com.example.huzz.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.huzz.R
import com.example.huzz.features.file.FileUtils
import com.example.huzz.model.HuzzFilePacket

/**
 * Modern chat-style file message display with document type icon,
 * sanitized filename, formatted size, and an Open action button.
 */
@Composable
fun FileMessageItem(
    packet: HuzzFilePacket,
    filePath: String? = null,
    onFileClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    val sanitizedFileName = remember(packet.fileName) {
        FileUtils.sanitizeFileName(packet.fileName)
    }

    androidx.compose.material3.Surface(
        modifier = modifier
            .fillMaxWidth(0.85f)
            .clickable {
                showDialog = true
                onFileClick()
            },
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, com.example.huzz.ui.theme.NothingBorder),
        color = com.example.huzz.ui.theme.NothingSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // MIME-specific file icon
            Icon(
                imageVector = Icons.Filled.Description,
                contentDescription = stringResource(R.string.cd_file),
                tint = com.example.huzz.ui.theme.NothingTextSecondary,
                modifier = Modifier.size(28.dp)
            )

            // File metadata: sanitized name, size, type badge
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = sanitizedFileName,
                    fontFamily = com.example.huzz.ui.theme.SpaceGroteskFamily,
                    color = com.example.huzz.ui.theme.NothingTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = FileUtils.formatFileSize(packet.fileSize),
                        fontFamily = com.example.huzz.ui.theme.SpaceMonoFamily,
                        fontSize = 10.sp,
                        color = com.example.huzz.ui.theme.NothingTextTertiary
                    )

                    FileTypeBadge(mimeType = packet.mimeType, fileName = sanitizedFileName)
                }
            }

            // Explicit Open action button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, com.example.huzz.ui.theme.NothingBorderHighlight, RoundedCornerShape(4.dp))
                    .background(com.example.huzz.ui.theme.NothingBlack)
                    .clickable {
                        showDialog = true
                        onFileClick()
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "[OPEN]",
                    fontFamily = com.example.huzz.ui.theme.SpaceMonoFamily,
                    color = com.example.huzz.ui.theme.NothingTextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }

    // File viewer / save dialog
    if (showDialog) {
        FileViewerDialog(
            packet = packet,
            directFilePath = filePath,
            onDismiss = { showDialog = false }
        )
    }
}

/**
 * Small badge showing file type in Nothing Space Mono style
 */
@Composable
private fun FileTypeBadge(mimeType: String, fileName: String = "") {
    val ext = fileName.substringAfterLast(".", "").lowercase()
    val text = when {
        mimeType.startsWith("application/pdf") || ext == "pdf" -> "PDF"
        ext == "docx" || ext == "doc" || mimeType.contains("wordprocessingml") -> "DOC"
        ext == "xlsx" || ext == "xls" || mimeType.contains("spreadsheetml") -> "XLS"
        ext == "csv" || mimeType == "text/csv" -> "CSV"
        ext == "md" || mimeType == "text/markdown" -> "MD"
        mimeType.startsWith("text/") || ext == "txt" -> "TXT"
        mimeType.startsWith("image/") -> "IMG"
        mimeType.startsWith("audio/") -> "AUD"
        mimeType.startsWith("video/") -> "VID"
        mimeType.contains("zip") || mimeType.contains("rar") || ext in listOf("zip", "rar", "7z", "tar", "gz") -> "ZIP"
        ext == "apk" -> "APK"
        else -> "FILE"
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .border(1.dp, Color(0xFF262626), RoundedCornerShape(3.dp))
            .background(Color(0xFF141414))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text = text,
            fontFamily = com.example.huzz.ui.theme.SpaceMonoFamily,
            color = com.example.huzz.ui.theme.NothingTextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp
        )
    }
}

/**
 * Get appropriate icon color based on file extension
 */
private fun getFileIconColor(fileName: String): Color {
    val extension = fileName.substringAfterLast(".", "").lowercase()
    return when (extension) {
        "pdf" -> Color(0xFFDC2626) // Red
        "doc", "docx" -> Color(0xFF1D4ED8) // Blue
        "xls", "xlsx" -> Color(0xFF059669) // Green
        "csv" -> Color(0xFF0D9488) // Teal
        "ppt", "pptx" -> Color(0xFFEA580C) // Orange
        "txt", "json", "xml", "md" -> Color(0xFF7C3AED) // Purple
        "jpg", "jpeg", "png", "gif", "webp" -> Color(0xFF2563EB) // Blue
        "mp3", "wav", "m4a", "ogg" -> Color(0xFFEA580C) // Orange
        "mp4", "avi", "mov", "mkv" -> Color(0xFFDC2626) // Red
        "zip", "rar", "7z", "gz", "tar" -> Color(0xFF7C2D12) // Brown
        else -> Color(0xFF6B7280) // Gray
    }
}
