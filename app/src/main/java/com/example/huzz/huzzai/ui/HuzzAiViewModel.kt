package com.example.huzz.huzzai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.huzz.huzzai.data.HuzzAiModelManager
import com.example.huzz.huzzai.domain.HuzzAiBatteryPolicy
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

    private var searchJob: Job? = null

    init {
        // Load initial category chunks
        loadDefaultCategory(null)
    }

    fun onQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()

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
        // Re-execute current query with newly active tier
        if (_searchQuery.value.isNotBlank()) {
            onQueryChanged(_searchQuery.value)
        }
    }
}
