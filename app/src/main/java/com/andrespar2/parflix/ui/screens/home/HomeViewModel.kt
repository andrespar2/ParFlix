package com.andrespar2.parflix.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.andrespar2.parflix.AppContainer
import com.andrespar2.parflix.data.local.entity.CatalogItemEntity
import com.andrespar2.parflix.data.repository.CatalogRepository
import com.andrespar2.parflix.data.repository.LibraryRepository
import com.andrespar2.parflix.domain.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Estado de la pantalla de inicio. La sincronización se lanza aquí de forma
 * oportunista (el trabajo real está protegido por un mutex en el motor).
 */
class HomeViewModel(
    private val catalogRepository: CatalogRepository,
    libraryRepository: LibraryRepository,
) : ViewModel() {

    val isOnline = catalogRepository.isOnline
    val syncProgress = catalogRepository.syncProgress

    val highlights: Flow<List<CatalogItemEntity>> = catalogRepository.observeHighlights(6)
    val continueWatching = libraryRepository.continueWatching
    val recentMovies: Flow<List<CatalogItemEntity>> =
        catalogRepository.observeRecentlyAdded(MediaType.MOVIE, 20)
    val recentSeries: Flow<List<CatalogItemEntity>> =
        catalogRepository.observeRecentlyAdded(MediaType.SERIES, 20)
    val recentAnime: Flow<List<CatalogItemEntity>> =
        catalogRepository.observeRecentlyAdded(MediaType.ANIME, 20)
    val topRated: Flow<List<CatalogItemEntity>> = catalogRepository.observeTopRated(20)
    val totalCount: Flow<Int> = catalogRepository.observeTotalCount()

    init {
        // Primera sincronización solo si el catálogo local está vacío.
        viewModelScope.launch { catalogRepository.initialSyncIfNeeded() }
    }

    fun refresh() {
        viewModelScope.launch { catalogRepository.syncOnOpen() }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                HomeViewModel(container.catalogRepository, container.libraryRepository)
            }
        }
    }
}
