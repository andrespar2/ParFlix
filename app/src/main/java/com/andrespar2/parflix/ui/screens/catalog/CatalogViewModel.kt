package com.andrespar2.parflix.ui.screens.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.andrespar2.parflix.AppContainer
import com.andrespar2.parflix.data.local.entity.CatalogItemEntity
import com.andrespar2.parflix.data.local.entity.GenreEntity
import com.andrespar2.parflix.data.repository.CatalogRepository
import com.andrespar2.parflix.domain.MediaType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Cuadrícula de un tipo de contenido (Películas, Series o Anime).
 *
 * La paginación se resuelve sobre la base local: se amplía el número de
 * elementos visibles, sin volver a descargar nada.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModel(
    private val catalogRepository: CatalogRepository,
    val mediaType: MediaType,
) : ViewModel() {

    private data class Query(val visible: Int, val genreId: Int?)

    private val queryState = MutableStateFlow(Query(visible = PAGE_SIZE, genreId = null))

    val items: Flow<List<CatalogItemEntity>> = queryState.flatMapLatest { query ->
        val genreId = query.genreId
        if (genreId == null) {
            catalogRepository.observePage(mediaType, query.visible, 0)
        } else {
            catalogRepository.observeByGenre(genreId, mediaType, query.visible)
        }
    }

    val genres: Flow<List<GenreEntity>> = catalogRepository.observeGenres(mediaType)
    val totalCount: Flow<Int> = catalogRepository.observeCount(mediaType)

    private val _selectedGenre = MutableStateFlow<Int?>(null)
    val selectedGenre: StateFlow<Int?> = _selectedGenre.asStateFlow()

    init {
        viewModelScope.launch { catalogRepository.initialSyncIfNeeded() }
    }

    fun selectGenre(genreId: Int?) {
        _selectedGenre.value = genreId
        queryState.update { it.copy(genreId = genreId, visible = PAGE_SIZE) }
    }

    fun loadMore() {
        queryState.update { it.copy(visible = it.visible + PAGE_SIZE) }
    }

    companion object {
        private const val PAGE_SIZE = 60

        fun factory(container: AppContainer, mediaType: MediaType): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { CatalogViewModel(container.catalogRepository, mediaType) }
            }
    }
}
