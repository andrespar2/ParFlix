package com.andrespar2.parflix.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.andrespar2.parflix.AppContainer
import com.andrespar2.parflix.data.local.entity.FavoriteEntity
import com.andrespar2.parflix.data.local.entity.WatchHistoryEntity
import com.andrespar2.parflix.data.repository.LibraryRepository
import com.andrespar2.parflix.domain.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** "Mi lista" (favoritos) y "Continuar viendo" (historial). Todo local. */
class LibraryViewModel(
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    val favorites: Flow<List<FavoriteEntity>> = libraryRepository.favorites
    val continueWatching: Flow<List<WatchHistoryEntity>> = libraryRepository.continueWatching

    fun removeFavorite(tmdbId: Int, mediaType: MediaType) {
        viewModelScope.launch { libraryRepository.removeFavorite(tmdbId, mediaType) }
    }

    fun removeFromHistory(tmdbId: Int, mediaType: MediaType) {
        viewModelScope.launch { libraryRepository.removeFromHistory(tmdbId, mediaType) }
    }

    fun clearHistory() {
        viewModelScope.launch { libraryRepository.clearHistory() }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { LibraryViewModel(container.libraryRepository) }
        }
    }
}
