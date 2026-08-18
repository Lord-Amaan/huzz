package com.example.huzz

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.huzz.ui.theme.HuzzTheme

class MainActivity : ComponentActivity() {

    private lateinit var nearbyChatManager: NearbyChatManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nearbyChatManager = NearbyChatManager(this)
        enableEdgeToEdge()
        setContent {
            HuzzTheme {
                ChatScreen(nearbyChatManager)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        nearbyChatManager.stopAll()
    }
}

@Composable
fun ChatScreen(nearbyChatManager: NearbyChatManager) {
    val context = LocalContext.current
    val status by nearbyChatManager.status.collectAsStateWithLifecycle()
    val error by nearbyChatManager.error.collectAsStateWithLifecycle()
    val discoveredPeers by nearbyChatManager.discoveredPeers.collectAsStateWithLifecycle()
    val messages by nearbyChatManager.messages.collectAsStateWithLifecycle()

    var username by remember { mutableStateOf("User") }
    var messageText by remember { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.NEARBY_WIFI_DEVICES
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            pendingAction?.invoke()
            pendingAction = null
        } else {
            Toast.makeText(context, "Permissions required for Nearby Chat", Toast.LENGTH_SHORT).show()
        }
    }

    fun executeWithPermissions(action: () -> Unit) {
        val allGranted = permissionsToRequest.all {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            action()
        } else {
            pendingAction = action
            launcher.launch(permissionsToRequest)
        }
    }

    LaunchedEffect(error) {
        error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (status == ConnectionStatus.Connected) {
                MessageInput(
                    messageText = messageText,
                    onMessageChange = { messageText = it },
                    onSend = {
                        nearbyChatManager.sendMessage(messageText)
                        messageText = ""
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text(
                text = "Nearby Chat",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Status: ${status.name}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (status == ConnectionStatus.Connected) Color.Green else Color.Gray
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (status != ConnectionStatus.Connected) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            executeWithPermissions { nearbyChatManager.startAdvertising(username) }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Advertise")
                    }
                    Button(
                        onClick = {
                            executeWithPermissions { nearbyChatManager.startDiscovery() }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Discover")
                    }
                }

                if (status != ConnectionStatus.Disconnected) {
                    Button(
                        onClick = { nearbyChatManager.stopAll() },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Stop")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (status == ConnectionStatus.Discovering) {
                    Text("Discovered Peers:", fontWeight = FontWeight.Bold)
                    LazyColumn {
                        items(discoveredPeers.toList()) { (id, name) ->
                            PeerItem(name) {
                                nearbyChatManager.connectToPeer(id, username)
                            }
                        }
                    }
                }
            } else {
                // Connected UI: Show messages
                MessageList(messages = messages, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun PeerItem(name: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = name, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = "Connect", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun MessageList(messages: List<ChatMessage>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        reverseLayout = true
    ) {
        items(messages.reversed()) { message ->
            MessageBubble(message)
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (message.isFromMe) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (message.isFromMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                if (!message.isFromMe) {
                    Text(
                        text = message.sender,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(text = message.messageText)
            }
        }
    }
}

@Composable
fun MessageInput(
    messageText: String,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = onMessageChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message") }
            )
            IconButton(onClick = onSend, enabled = messageText.isNotBlank()) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send"
                )
            }
        }
    }
}
