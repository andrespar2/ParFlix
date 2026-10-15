package com.andrespar2.parflix.ui.screens.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.andrespar2.parflix.AppContainer
import com.andrespar2.parflix.data.local.entity.CatalogItemEntity
import com.andrespar2.parflix.data.local.entity.EpisodeEntity
import com.andrespar2.parflix.data.repository.CatalogRepository
import com.andrespar2.parflix.data.repository.DetailsRepository
import com.andrespar2.parflix.data.repository.LibraryRepository
import com.andrespar2.parflix.domain.MediaDetailsView
import com.andrespar2.parflix.domain.MediaType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Ficha de detalle: metadatos de TMDB (enriquecidos al abrir), disponibilidad
 * de Vimeus y temporadas/episodios bajo demanda.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DetailsViewModel(
    private val detailsRepository: DetailsRepository,
    private val catalogRepository: CatalogRepository,
    private val libraryRepository: LibraryRepository,
    private val tmdbId: Int,
    val mediaType: MediaType,
) : ViewModel() {

    val details: Flow<MediaDetailsView?> = detailsRepository.observeDetails(tmdbId, mediaType)
    val isFavorite: Flow<Boolean> = libraryRepository.observeIsFavorite(tmdbId, mediaType)

    private val _selectedSeason = MutableStateFlow(1)
    val selectedSeason: StateFlow<Int> = _selectedSeason.asStateFlow()

    val episodes: Flow<List<EpisodeEntity>> = _selectedSeason.flatMapLatest { season ->
        detailsRepository.observeSeasonEpisodes(tmdbId, mediaType, season)
    }

    private val _loadingEpisodes = MutableStateFlow(false)
    val loadingEpisodes: StateFlow<Boolean> = _loadingEpisodes.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var initialized = false

    init {
        viewModelScope.launch { enrichNow() }
    }

    private suspend fun enrichNow() {
        val item = catalogRepository.findItem(tmdbId, mediaType) ?: return
        detailsRepository.enrich(item)
    }

    /** Se invoca cuando la ficha ya conoce sus temporadas. */
    fun onSeasonsKnown(seasons: List<Int>) {
        if (initialized || seasons.isEmpty()) return
        initialized = true
        val first = seasons.first()
        _selectedSeason.value = first
        loadSeason(first)
    }

    fun selectSeason(season: Int) {
        _selectedSeason.value = season
        loadSeason(season)
    }

    private fun loadSeason(season: Int) {
        viewModelScope.launch {
            _loadingEpisodes.value = true
            detailsRepository.ensureSeason(tmdbId, mediaType, season)
            _loadingEpisodes.value = false
        }
    }

    fun toggleFavorite(item: CatalogItemEntity) {
        viewModelScope.launch {
            val nowFavorite = libraryRepository.toggleFavorite(item)
            _message.value = if (nowFavorite) "Añadido a Mi lista" else "Quitado de Mi lista"
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    /** Registra la reproducción (para "Continuar viendo"). */
    fun recordWatch(item: CatalogItemEntity, season: Int?, episode: Int?, episodeTitle: String?) {
        viewModelScope.launch {
            libraryRepository.recordWatch(
                item = item,
                season = season,
                episode = episode,
                episodeTitle = episodeTitle,
            )
        }
    }

    companion object {
        fun factory(
            container: AppContainer,
            mediaType: MediaType,
            tmdbId: Int,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DetailsViewModel(
                    detailsRepository = container.detailsRepository,
                    catalogRepository = container.catalogRepository,
                    libraryRepository = container.libraryRepository,
                    tmdbId = tmdbId,
                    mediaType = mediaType,
                )
            }
        }
    }
}
