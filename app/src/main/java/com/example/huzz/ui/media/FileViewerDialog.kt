package com.example.huzz.ui.media

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.example.huzz.R
import com.example.huzz.features.file.FileUtils
import com.example.huzz.model.HuzzFilePacket
import java.io.File

/**
 * Dialog for viewing, opening, and saving received file attachments in modern chat style.
 */
@Composable
fun FileViewerDialog(
    packet: HuzzFilePacket,
    directFilePath: String? = null,
    onDismiss: () -> Unit,
    onSaveToDevice: ((ByteArray, String) -> Unit)? = null
) {
    val context = LocalContext.current
    val sanitizedName = FileUtils.sanitizeFileName(packet.fileName)

    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header icon & title
                Icon(
                    imageVector = Icons.Filled.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )

                Text(
                    text = stringResource(R.string.file_viewer_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )

                // File metadata
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = stringResource(R.string.file_viewer_name, sanitizedName),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.file_viewer_size, FileUtils.formatFileSize(packet.fileSize)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.file_viewer_type, packet.mimeType),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Action buttons: Open, Save, Close
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Open via external viewer
                        Button(
                            onClick = {
                                tryOpenFile(context, packet, directFilePath)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(stringResource(R.string.file_viewer_open))
                        }

                        // Save to Downloads
                        Button(
                            onClick = {
                                val bytesToSave = if (packet.content.isNotEmpty()) {
                                    packet.content
                                } else if (!directFilePath.isNullOrBlank() && File(directFilePath).exists()) {
                                    try { File(directFilePath).readBytes() } catch (_: Exception) { ByteArray(0) }
                                } else {
                                    ByteArray(0)
                                }
                                val saved = FileUtils.saveToDownloads(context, sanitizedName, bytesToSave)
                                if (saved) {
                                    Toast.makeText(context, context.getString(R.string.toast_file_saved), Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, context.getString(R.string.toast_file_save_failed), Toast.LENGTH_SHORT).show()
                                }
                                onSaveToDevice?.invoke(bytesToSave, sanitizedName)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(stringResource(R.string.file_viewer_save))
                        }
                    }

                    // Dismiss button
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.close_with_emoji))
                    }
                }
            }
        }
    }
}

/**
 * Attempts to open a file using system viewers via sandboxed FileProvider.
 * Enforces read-only access with FLAG_GRANT_READ_URI_PERMISSION.
 */
fun tryOpenFile(context: Context, packet: HuzzFilePacket, directFilePath: String? = null) {
    try {
        val targetFile = if (!directFilePath.isNullOrBlank() && File(directFilePath).exists()) {
            File(directFilePath)
        } else {
            val safeName = FileUtils.sanitizeFileName(packet.fileName)
            val docDir = File(context.cacheDir, "documents").apply { mkdirs() }
            val f = File(docDir, safeName)
            if (!f.exists() || f.length() != packet.fileSize) {
                if (packet.content.isNotEmpty()) {
                    f.writeBytes(packet.content)
                }
            }
            f
        }

        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, targetFile)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, packet.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                context,
                context.getString(R.string.toast_no_app_for_file),
                Toast.LENGTH_SHORT
            ).show()
        }
    } catch (e: Exception) {
        android.util.Log.e("FileViewerDialog", "Error opening file via FileProvider", e)
        Toast.makeText(
            context,
            "Could not open file: ${e.message}",
            Toast.LENGTH_SHORT
        ).show()
    }
}
