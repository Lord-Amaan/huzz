package com.example.huzz.ui.media

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.huzz.core.ui.component.text.AnnotatedClickableText
import com.example.huzz.model.HuzzMessage
import com.example.huzz.ui.DeliveryStatusIcon
import com.example.huzz.ui.formatTextMessageMetadata
import com.example.huzz.ui.formatTextMessageSender
import com.example.huzz.ui.isFromSelf
import com.example.huzz.ui.peerIdentityForMessage
import com.example.huzz.ui.theme.HuzzFontFamily
import com.example.huzz.ui.theme.ChatVisualTokens
import com.example.huzz.ui.theme.LocalHuzzPalette
import com.example.huzz.ui.theme.MessageSenderTextStyle
import com.example.huzz.ui.theme.colorForPeer
import java.text.SimpleDateFormat

/**
 * Classic messenger shell for media messages (images, voice notes) in `ChatUiMode.Bubbles`,
 * mirroring the text-bubble treatment: the author's identity colour washes the fill and
 * hairline, the speaker-side corner tightens into a tail, the sender's name heads the first
 * bubble of a run, and the timestamp (plus delivery ticks for own private messages) rides
 * flush-right beneath the media. The shell itself is long-clickable so media bubbles always
 * open the message action sheet even when no sender label is shown and the media consumes
 * touches (voice-note player controls).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaBubbleShell(
    message: HuzzMessage,
    currentUserNickname: String,
    myPeerID: String,
    showSender: Boolean,
    timeFormatter: SimpleDateFormat,
    onNicknameClick: ((String) -> Unit)?,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val palette = LocalHuzzPalette.current
    val haptic = LocalHapticFeedback.current
    val isSelf = message.isFromSelf(currentUserNickname, myPeerID)

    val authorColor = remember(message, isSelf, palette) {
        if (isSelf) palette.accentOrange else colorForPeer(peerIdentityForMessage(message), palette)
    }

    val corner = ChatVisualTokens.BubbleCornerRadius
    val tail = ChatVisualTokens.BubbleTailRadius
    val bubbleShape = if (isSelf) {
        RoundedCornerShape(topStart = corner, topEnd = corner, bottomEnd = tail, bottomStart = corner)
    } else {
        RoundedCornerShape(topStart = corner, topEnd = corner, bottomEnd = corner, bottomStart = tail)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start,
    ) {
        val bubbleBg = if (isSelf) androidx.compose.ui.graphics.Color(0xFF141414) else androidx.compose.ui.graphics.Color(0xFF0C0C0C)
        val bubbleBorder = if (isSelf) androidx.compose.ui.graphics.Color(0xFF2E2E2E) else androidx.compose.ui.graphics.Color(0xFF222222)

        Box(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = bubbleBorder,
                    shape = bubbleShape
                )
                .background(
                    color = bubbleBg,
                    shape = bubbleShape
                )
                .combinedClickable(
                    enabled = onLongPress != null,
                    onClick = {},
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress?.invoke()
                    },
                )
                .padding(
                    horizontal = ChatVisualTokens.BubblePaddingHorizontal,
                    vertical = ChatVisualTokens.BubblePaddingVertical,
                )
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (showSender && !isSelf) {
                    val senderText = remember(message, currentUserNickname, myPeerID, palette) {
                        formatTextMessageSender(
                            message = message,
                            currentUserNickname = currentUserNickname,
                            myPeerID = myPeerID,
                            palette = palette,
                        )
                    }
                    AnnotatedClickableText(
                        text = senderText,
                        annotationTags = listOf("nickname_click"),
                        onAnnotationClick = { tag, item ->
                            if (tag == "nickname_click" && onNicknameClick != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNicknameClick.invoke(item)
                                true
                            } else {
                                false
                            }
                        },
                        onLongPress = { onLongPress?.invoke() },
                        fontFamily = HuzzFontFamily,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        style = MessageSenderTextStyle,
                    )
                }

                content()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(
                        text = formatTextMessageMetadata(message, timeFormatter),
                        fontFamily = HuzzFontFamily,
                    )
                    if (isSelf && message.isPrivate) {
                        message.deliveryStatus?.let { status ->
                            Spacer(Modifier.width(4.dp))
                            DeliveryStatusIcon(status = status)
                        }
                    }
                }
            }
        }
    }
}
