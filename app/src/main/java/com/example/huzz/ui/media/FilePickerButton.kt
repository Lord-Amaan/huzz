package com.example.huzz.ui.media

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.huzz.R
import com.example.huzz.features.file.FileUtils
import com.example.huzz.ui.ComposerActionSurface
import com.example.huzz.ui.ComposerIconSize

/** Supported document MIME types according to Phase 1 specification */
private val DocumentMimeTypes = arrayOf(
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/msword",
    "application/vnd.ms-excel",
    "text/plain",
    "text/csv",
    "text/markdown",
    "application/octet-stream",
    "*/*"
)

/**
 * Modern document/file attachment picker button for the chat composer.
 * Harmonizes with the camera, microphone, and send buttons using ComposerActionSurface.
 */
@Composable
fun FilePickerButton(
    modifier: Modifier = Modifier,
    onFileReady: (String) -> Unit
) {
    val context = LocalContext.current

    // SAF Document Picker
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            val path = FileUtils.copyFileForSending(context, uri)
            if (!path.isNullOrBlank()) {
                onFileReady(path)
            }
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    ComposerActionSurface(
        isActive = false,
        isPressed = isPressed,
        modifier = modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = {
                filePicker.launch(DocumentMimeTypes)
            }
        )
    ) { tint ->
        Icon(
            imageVector = Icons.Filled.Attachment,
            contentDescription = stringResource(R.string.cd_pick_file),
            tint = tint,
            modifier = Modifier
                .size(ComposerIconSize)
                .rotate(90f)
        )
    }
}
