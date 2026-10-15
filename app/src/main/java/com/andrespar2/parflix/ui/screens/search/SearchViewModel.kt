package com.andrespar2.parflix.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.andrespar2.parflix.AppContainer
import com.andrespar2.parflix.data.local.entity.SearchQueryEntity
import com.andrespar2.parflix.data.repository.CatalogRepository
import com.andrespar2.parflix.data.repository.SearchRepository
import com.andrespar2.parflix.data.repository.SearchResult
import com.andrespar2.parflix.domain.MediaType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val result: SearchResult = SearchResult(),
    val error: String? = null,
)

/**
 * Búsqueda inteligente local con retardo (debounce) para no consultar en cada
 * pulsación. El complemento remoto de TMDB solo se usa si no hay resultados
 * locales.
 */
class SearchViewModel(
    private val searchRepository: SearchRepository,
    catalogRepository: CatalogRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val _filter = MutableStateFlow<MediaType?>(null)
    val filter: StateFlow<MediaType?> = _filter.asStateFlow()

    val recent: Flow<List<SearchQueryEntity>> = searchRepository.observeRecentQueries(10)
    val isOnline = catalogRepository.isOnline

    private var searchJob: Job? = null

    fun onQueryChange(raw: String) {
        _state.update { it.copy(query = raw) }
        searchJob?.cancel()
        val trimmed = raw.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) {
            _state.update { it.copy(loading = false, result = SearchResult(), error = null) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            _state.update { it.copy(loading = true, error = null) }
            val result = runCatching {
                searchRepository.search(trimmed, _filter.value, allowRemote = true)
            }
            result.onSuccess { res ->
                _state.update { it.copy(loading = false, result = res) }
            }.onFailure { t ->
                _state.update {
                    it.copy(loading = false, error = t.message ?: "No se pudo completar la búsqueda")
                }
            }
        }
    }

    /** Guarda la consulta en el historial local y la ejecuta. */
    fun submit(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) return
        viewModelScope.launch { searchRepository.rememberQuery(trimmed) }
        onQueryChange(trimmed)
    }

    fun selectSuggestion(raw: String) {
        _state.update { it.copy(query = raw) }
        submit(raw)
    }

    fun setFilter(mediaType: MediaType?) {
        _filter.value = mediaType
        if (_state.value.query.trim().length >= MIN_QUERY_LENGTH) {
            onQueryChange(_state.value.query)
        }
    }

    fun clearQuery() {
        searchJob?.cancel()
        _state.value = SearchUiState()
    }

    fun forgetRecent(query: String) {
        viewModelScope.launch { searchRepository.forgetQuery(query) }
    }

    fun clearRecent() {
        viewModelScope.launch { searchRepository.clearRecentQueries() }
    }

    companion object {
        private const val MIN_QUERY_LENGTH = 2
        private const val DEBOUNCE_MS = 280L

        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                SearchViewModel(container.searchRepository, container.catalogRepository)
            }
        }
    }
}
