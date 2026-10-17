package com.example.huzz.huzzai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.huzz.huzzai.data.HuzzAiModelManager
import com.example.huzz.huzzai.domain.HuzzAiBatteryPolicy
import com.example.huzz.huzzai.domain.HuzzAiInferenceEngine
import com.example.huzz.huzzai.domain.HuzzAiSearchEngine
import com.example.huzz.huzzai.mesh.HuzzAiMeshCoordinator
import com.example.huzz.huzzai.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel managing HuzzAi UI states, search execution, model downloading, and mesh queries.
 */
class HuzzAiViewModel(application: Application) : AndroidViewModel(application) {

    private val searchEngine = HuzzAiSearchEngine.getInstance(application)
    val modelManager = HuzzAiModelManager.getInstance(application)
    val meshCoordinator = HuzzAiMeshCoordinator.getInstance(application)
    val batteryPolicy = HuzzAiBatteryPolicy(application)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<HuzzAiCategory?>(null)
    val selectedCategory: StateFlow<HuzzAiCategory?> = _selectedCategory.asStateFlow()

    private val _searchResult = MutableStateFlow<HuzzAiSearchResult?>(null)
    val searchResult: StateFlow<HuzzAiSearchResult?> = _searchResult.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _showModelHub = MutableStateFlow(false)
    val showModelHub: StateFlow<Boolean> = _showModelHub.asStateFlow()

    private val _selectedChunkForDetail = MutableStateFlow<HuzzAiChunk?>(null)
    val selectedChunkForDetail: StateFlow<HuzzAiChunk?> = _selectedChunkForDetail.asStateFlow()

    /** Streaming on-device answer for the current question, or null before the user asks. */
    data class AiAnswerState(
        val query: String,
        val text: String = "",
        val streaming: Boolean = true,
        val loadingModel: HuzzAiModelTier? = null,
        val fromModel: Boolean = false,
        val tier: HuzzAiModelTier? = null,
        val note: String? = null,
        val latencyMs: Long = 0L
    )

    private val _aiAnswer = MutableStateFlow<AiAnswerState?>(null)
    val aiAnswer: StateFlow<AiAnswerState?> = _aiAnswer.asStateFlow()

    private val prefs = application.getSharedPreferences("huzzai_ui_prefs", android.content.Context.MODE_PRIVATE)
    private val _downloadPromptDismissed = MutableStateFlow(prefs.getBoolean(KEY_PROMPT_DISMISSED, false))
    val downloadPromptDismissed: StateFlow<Boolean> = _downloadPromptDismissed.asStateFlow()

    private var searchJob: Job? = null
    private var answerJob: Job? = null

    init {
        // Load initial category chunks
        loadDefaultCategory(null)
    }

    fun onQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        // Editing the question invalidates the previous AI answer.
        if (_aiAnswer.value?.query != query.trim()) stopAi(clear = true)

        if (query.isBlank()) {
            loadDefaultCategory(_selectedCategory.value)
            return
        }

        searchJob = viewModelScope.launch {
            delay(150) // Small debounce for typing
            _isLoading.value = true
            try {
                val result = searchEngine.query(query, _selectedCategory.value)
                _searchResult.value = result
            } catch (t: Throwable) {
                android.util.Log.e("HuzzAiViewModel", "Error querying HuzzAi", t)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun onCategorySelected(category: HuzzAiCategory?) {
        val newCategory = if (_selectedCategory.value == category) null else category
        _selectedCategory.value = newCategory

        if (_searchQuery.value.isNotBlank()) {
            onQueryChanged(_searchQuery.value)
        } else {
            loadDefaultCategory(newCategory)
        }
    }

    private fun loadDefaultCategory(category: HuzzAiCategory?) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = searchEngine.loadCategoryManuals(category)
                _searchResult.value = result
            } catch (t: Throwable) {
                android.util.Log.e("HuzzAiViewModel", "Error loading category manuals", t)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Runs the on-device model on the current question, grounded in the matched manuals.
     * Without a model (or on low battery) this shows the best manual's steps instead.
     */
    fun askAi() {
        val query = _searchQuery.value.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        answerJob?.cancel()
        _aiAnswer.value = AiAnswerState(query = query)

        answerJob = viewModelScope.launch {
            val start = System.currentTimeMillis()
            try {
                val result = searchEngine.query(query, _selectedCategory.value)
                _searchResult.value = result
                if (result.localChunks.isEmpty()) {
                    _aiAnswer.value = null
                    return@launch
                }
                searchEngine.answerStream(query, result.localChunks).collect { event ->
                    val current = _aiAnswer.value ?: return@collect
                    _aiAnswer.value = when (event) {
                        is HuzzAiInferenceEngine.Event.LoadingModel -> current.copy(loadingModel = event.tier)
                        is HuzzAiInferenceEngine.Event.Partial -> current.copy(
                            text = event.text,
                            loadingModel = null,
                            fromModel = true,
                            tier = modelManager.activeTier.value
                        )
                        is HuzzAiInferenceEngine.Event.Done -> current.copy(
                            text = event.text,
                            streaming = false,
                            loadingModel = null,
                            fromModel = event.fromModel,
                            tier = if (event.fromModel) modelManager.activeTier.value else null,
                            note = event.note,
                            latencyMs = System.currentTimeMillis() - start
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                android.util.Log.e("HuzzAiViewModel", "Error answering with HuzzAi", t)
                _aiAnswer.value = _aiAnswer.value?.copy(streaming = false, loadingModel = null, note = "Could not generate an answer")
            } finally {
                _aiAnswer.value?.let { if (it.streaming) _aiAnswer.value = it.copy(streaming = false, loadingModel = null) }
            }
        }
    }

    fun stopAi(clear: Boolean = false) {
        answerJob?.cancel()
        answerJob = null
        if (clear) _aiAnswer.value = null
    }

    /** Called when the HuzzAi sheet closes: stop generating and free the model's RAM. */
    fun onSheetClosed() {
        stopAi()
        viewModelScope.launch { searchEngine.releaseModel() }
    }

    fun dismissDownloadPrompt() {
        prefs.edit().putBoolean(KEY_PROMPT_DISMISSED, true).apply()
        _downloadPromptDismissed.value = true
    }

    override fun onCleared() {
        super.onCleared()
        // viewModelScope is already cancelled here; release on a short-lived scope.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { searchEngine.releaseModel() }
    }

    fun openModelHub() {
        _showModelHub.value = true
    }

    fun closeModelHub() {
        _showModelHub.value = false
    }

    fun selectChunkDetail(chunk: HuzzAiChunk?) {
        _selectedChunkForDetail.value = chunk
    }

    fun downloadModel(tier: HuzzAiModelTier) {
        modelManager.startDownload(tier)
    }

    fun cancelModelDownload(tier: HuzzAiModelTier) {
        modelManager.cancelDownload(tier)
    }

    fun deleteModel(tier: HuzzAiModelTier) {
        modelManager.deleteModel(tier)
    }

    fun setActiveModelTier(tier: HuzzAiModelTier?) {
        modelManager.setActiveTier(tier)
        stopAi(clear = true)
        // Free the previous model; the new one loads on the next question.
        viewModelScope.launch { searchEngine.releaseModel() }
    }

    private companion object {
        const val KEY_PROMPT_DISMISSED = "download_prompt_dismissed"
    }
}
