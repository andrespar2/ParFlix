package com.andrespar2.parflix.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.andrespar2.parflix.AppContainer
import com.andrespar2.parflix.core.security.CredentialStore
import com.andrespar2.parflix.data.repository.CatalogRepository
import com.andrespar2.parflix.data.repository.DetailsRepository
import com.andrespar2.parflix.domain.MediaType
import com.andrespar2.parflix.player.PlaybackUrls
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayerUiState(
    val loading: Boolean = true,
    val url: String? = null,
    val title: String? = null,
    val error: String? = null,
)

/**
 * Reproductor. Construye la URL de inserción oficial de Vimeus a partir de los
 * datos locales y de las credenciales efectivas. Nunca registra la URL (puede
 * contener `view_key`).
 */
class PlayerViewModel(
    private val catalogRepository: CatalogRepository,
    private val detailsRepository: DetailsRepository,
    private val credentialStore: CredentialStore,
    private val mediaType: MediaType,
    private val tmdbId: Int,
    private val season: Int,
    private val episode: Int,
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = PlayerUiState(loading = true)
            val item = catalogRepository.findItem(tmdbId, mediaType)
            if (item == null) {
                _state.value = PlayerUiState(
                    loading = false,
                    error = "Este título no está en el catálogo local. " +
                        "Sincroniza el catálogo y vuelve a intentarlo.",
                )
                return@launch
            }

            var seasonArg = season.takeIf { it > 0 }
            var episodeArg = episode.takeIf { it > 0 }

            // El botón "Reproducir" de la ficha de una serie no conoce
            // temporada/episodio: se resuelve el primer episodio disponible.
            if (mediaType.isShow && (seasonArg == null || episodeArg == null)) {
                detailsRepository.firstPlayableEpisode(tmdbId, mediaType)?.let { (s, e) ->
                    seasonArg = s
                    episodeArg = e
                }
                // Sin episodios conocidos se asume el primero de la primera
                // temporada: es lo que anuncia el propio botón.
                if (seasonArg == null || episodeArg == null) {
                    seasonArg = 1
                    episodeArg = 1
                }
            }

            val storedEmbedUrl = if (mediaType.isShow && seasonArg != null && episodeArg != null) {
                detailsRepository.findEpisode(tmdbId, mediaType, seasonArg, episodeArg)?.embedUrl
            } else {
                item.embedUrl
            }

            val viewKey = credentialStore.current().vimeusViewKey
            val url = PlaybackUrls.build(
                mediaType = mediaType,
                tmdbId = item.tmdbId,
                imdbId = item.imdbId,
                season = seasonArg,
                episode = episodeArg,
                viewKey = viewKey,
                storedEmbedUrl = storedEmbedUrl,
            )

            _state.value = if (url == null) {
                PlayerUiState(
                    loading = false,
                    error = "No hay una URL de reproducción autorizada para este título. " +
                        "Comprueba las credenciales en Ajustes.",
                )
            } else {
                PlayerUiState(loading = false, url = url, title = item.title)
            }
        }
    }

    companion object {
        fun factory(
            container: AppContainer,
            mediaType: MediaType,
            tmdbId: Int,
            season: Int,
            episode: Int,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PlayerViewModel(
                    catalogRepository = container.catalogRepository,
                    detailsRepository = container.detailsRepository,
                    credentialStore = container.credentialStore,
                    mediaType = mediaType,
                    tmdbId = tmdbId,
                    season = season,
                    episode = episode,
                )
            }
        }
    }
}
