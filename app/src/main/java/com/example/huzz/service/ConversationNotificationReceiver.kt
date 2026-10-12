package com.example.huzz.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.example.huzz.model.HuzzMessage
import com.example.huzz.model.DeliveryStatus
import com.example.huzz.services.AppStateStore
import com.example.huzz.services.ContactDirectory
import com.example.huzz.services.MessageRouter
import com.example.huzz.ui.NotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID

/** Handles privacy-scoped direct reply and mark-read actions from DM notifications. */
class ConversationNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val conversationID = intent.getStringExtra(NotificationManager.EXTRA_PEER_ID)
            ?.let(ContactDirectory::canonicalConversationId)
            ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                var acknowledged = false
                when (intent.action) {
                    NotificationManager.ACTION_MARK_CONVERSATION_READ -> {
                        acknowledged =
                            AppStateStore.setPrivateConversationRead(conversationID, true)
                    }

                    NotificationManager.ACTION_REPLY_TO_CONVERSATION -> {
                        val reply = RemoteInput.getResultsFromIntent(intent)
                            ?.getCharSequence(NotificationManager.KEY_TEXT_REPLY)
                            ?.toString()
                            ?.trim()
                            ?.takeIf(String::isNotEmpty)
                            ?: return@launch
                        val mesh = MeshServiceHolder.getUnifiedOrCreate(
                            context.applicationContext
                        )
                        val message = HuzzMessage(
                            id = UUID.randomUUID().toString().uppercase(),
                            sender = mesh.myPeerID,
                            content = reply,
                            timestamp = Date(),
                            isPrivate = true,
                            recipientNickname = intent.getStringExtra(
                                NotificationManager.EXTRA_SENDER_NICKNAME
                            ),
                            senderPeerID = mesh.myPeerID,
                            deliveryStatus = DeliveryStatus.Sending
                        )
                        val persisted = AppStateStore.addPrivateMessageDurably(
                            peerID = conversationID,
                            msg = message,
                            forceRead = true
                        )
                        if (persisted) {
                            MessageRouter.getInstance(context.applicationContext, mesh)
                                .sendPrivate(
                                    content = reply,
                                    toPeerID = conversationID,
                                    recipientNickname = message.recipientNickname.orEmpty(),
                                    messageID = message.id
                                )
                            acknowledged =
                                AppStateStore.setPrivateConversationRead(conversationID, true)
                        }
                    }
                }
                if (acknowledged) {
                    NotificationManager.acknowledgeConversation(context, conversationID)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
