package com.example.huzz.huzzai.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.huzz.huzzai.model.HuzzAiCategory
import com.example.huzz.huzzai.model.HuzzAiChunk

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HuzzAiSheet(
    viewModel: HuzzAiViewModel,
    onDismiss: () -> Unit,
    onBroadcastMeshQuery: ((String, HuzzAiCategory) -> Unit)? = null
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchResult by viewModel.searchResult.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val showModelHub by viewModel.showModelHub.collectAsStateWithLifecycle()
    val selectedChunkForDetail by viewModel.selectedChunkForDetail.collectAsStateWithLifecycle()
    val activeTier by viewModel.modelManager.activeTier.collectAsStateWithLifecycle()
    val aiAnswer by viewModel.aiAnswer.collectAsStateWithLifecycle()
    val promptDismissed by viewModel.downloadPromptDismissed.collectAsStateWithLifecycle()
    val downloadStates by viewModel.modelManager.downloadStates.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    // Free the on-device model's RAM when HuzzAi is closed.
    DisposableEffect(Unit) {
        onDispose { viewModel.onSheetClosed() }
    }

    fun ask() {
        focusManager.clearFocus()
        viewModel.askAi()
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    com.example.huzz.core.ui.component.sheet.HuzzBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.95f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = "HuzzAi",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "HuzzAi",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SuggestionChip(
                        onClick = { viewModel.openModelHub() },
                        label = {
                            Text(
                                text = activeTier?.displayName?.substringBefore(" ") ?: "Fast FTS5",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = if (activeTier != null) Icons.Default.AutoAwesome else Icons.Default.FlashOn,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                }

                Row {
                    IconButton(onClick = { viewModel.openModelHub() }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Model Hub Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onQueryChanged(it) },
                placeholder = { Text("Search emergency guidance (e.g. purify water, CPR)") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChanged("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { ask() }),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Ask button: runs the on-device model (or shows manual steps without one)
            if (searchQuery.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                val streaming = aiAnswer?.streaming == true
                Button(
                    onClick = { if (streaming) viewModel.stopAi() else ask() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (streaming) Icons.Default.Stop else Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        when {
                            streaming -> "Stop"
                            activeTier != null -> "Ask HuzzAi (offline AI)"
                            else -> "Get answer"
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { viewModel.onCategorySelected(null) },
                    label = { Text("All Protocols") }
                )
                HuzzAiCategory.entries.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { viewModel.onCategorySelected(category) },
                        label = { Text(category.displayName) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Content List
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val chunks = searchResult?.localChunks ?: emptyList()
                val answer = aiAnswer?.takeIf { it.query == searchQuery.trim() }
                val anyModelOnDisk = downloadStates.values.any {
                    it is com.example.huzz.huzzai.model.ModelDownloadState.Ready ||
                        it is com.example.huzz.huzzai.model.ModelDownloadState.Downloading
                }

                if (chunks.isEmpty() && searchQuery.isNotBlank()) {
                    // Zero hits state -> Ask the Mesh fallback
                    ZeroHitsFallback(
                        query = searchQuery,
                        category = selectedCategory ?: HuzzAiCategory.FIRST_AID,
                        onAskMesh = {
                            onBroadcastMeshQuery?.invoke(searchQuery, selectedCategory ?: HuzzAiCategory.FIRST_AID)
                        }
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(bottom = 16.dp)
                    ) {
                        // Nudge to download a model while internet is available
                        if (activeTier == null && !anyModelOnDisk && !promptDismissed) {
                            item {
                                DownloadModelPrompt(
                                    recommended = viewModel.modelManager.recommendedTier,
                                    onOpenHub = { viewModel.openModelHub() },
                                    onDismiss = { viewModel.dismissDownloadPrompt() }
                                )
                            }
                        }

                        // On-device AI answer (streams while generating)
                        if (answer != null) {
                            item { AiAnswerCard(answer) }
                        }

                        // Verified Manual Cards
                        items(chunks) { chunk ->
                            ManualChunkCard(
                                chunk = chunk,
                                onClick = { viewModel.selectChunkDetail(chunk) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Model Hub Sheet
    if (showModelHub) {
        HuzzAiModelHubSheet(
            viewModel = viewModel,
            onDismiss = { viewModel.closeModelHub() }
        )
    }

    // Detail Dialog for Full Manual
    selectedChunkForDetail?.let { chunk ->
        ManualDetailDialog(
            chunk = chunk,
            onDismiss = { viewModel.selectChunkDetail(null) }
        )
    }
}

@Composable
private fun DownloadModelPrompt(
    recommended: com.example.huzz.huzzai.model.HuzzAiModelTier,
    onOpenHub: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Get written AI answers, fully offline", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Download a model while you have internet. It runs on your phone with no connection. " +
                    "Recommended for this phone: ${recommended.displayName} (${recommended.downloadSizeBytes / 1_000_000} MB).",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenHub) { Text("Choose model") }
                TextButton(onClick = onDismiss) { Text("Not now") }
            }
        }
    }
}

@Composable
private fun AiAnswerCard(answer: HuzzAiViewModel.AiAnswerState) {
    val loading = answer.loadingModel
    when {
        loading != null -> GroundedSynthesisCard(
            responseMarkdown = "Loading ${loading.displayName} into memory. The first answer takes a few seconds.",
            tier = loading,
            latencyMs = 0L,
            title = "Preparing offline AI",
            showProgress = true
        )
        answer.text.isEmpty() && answer.streaming -> GroundedSynthesisCard(
            responseMarkdown = "Thinking...",
            tier = null,
            latencyMs = 0L,
            showProgress = true
        )
        else -> GroundedSynthesisCard(
            responseMarkdown = answer.text + if (answer.streaming) " ▍" else "",
            tier = answer.tier,
            latencyMs = answer.latencyMs,
            title = if (answer.fromModel || answer.streaming) "AI Synthesized Guidance" else "Manual Guidance",
            note = answer.note
        )
    }
}

@Composable
private fun GroundedSynthesisCard(
    responseMarkdown: String,
    tier: com.example.huzz.huzzai.model.HuzzAiModelTier?,
    latencyMs: Long,
    title: String = "AI Synthesized Guidance",
    note: String? = null,
    showProgress: Boolean = false
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (tier != null || latencyMs > 0) Text(
                    text = listOfNotNull(tier?.parameterSize, latencyMs.takeIf { it > 0 }?.let { "${it}ms" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (showProgress) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
            }
            note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            Text(
                text = responseMarkdown,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Life in danger? Contact emergency services (e.g. 112) or nearby people first.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ManualChunkCard(
    chunk: HuzzAiChunk,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chunk.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                AssistChip(
                    onClick = {},
                    label = { Text(chunk.category.displayName, fontSize = 10.sp) }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = chunk.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (chunk.steps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    chunk.steps.take(3).forEachIndexed { index, step ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = "${index + 1}.",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                modifier = Modifier.width(18.dp)
                            )
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2
                            )
                        }
                    }
                    if (chunk.steps.size > 3) {
                        Text(
                            text = "+ ${chunk.steps.size - 3} more steps...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 18.dp, top = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📖 ${chunk.sourceManual}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "View Full →",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ZeroHitsFallback(
    query: String,
    category: HuzzAiCategory,
    onAskMesh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(54.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Local Manual Found",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Your offline library does not have an entry for \"$query\". You can broadcast an emergency query across the local BLE mesh.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onAskMesh,
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.CellTower, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Ask the Mesh")
        }
    }
}

@Composable
private fun ManualDetailDialog(
    chunk: HuzzAiChunk,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(text = chunk.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(text = "Source: ${chunk.sourceManual}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    Text(
                        text = chunk.contentMarkdown,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp
                    )
                    if (chunk.steps.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Action Steps:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        chunk.steps.forEachIndexed { i, step ->
                            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text("${i + 1}. ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(step, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
