package com.andrespar2.parflix.ui.screens.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.andrespar2.parflix.AppContainer
import com.andrespar2.parflix.ads.AdsDistribution
import com.andrespar2.parflix.ads.AdsManager
import com.andrespar2.parflix.data.local.entity.CatalogItemEntity
import com.andrespar2.parflix.data.local.entity.GenreEntity
import com.andrespar2.parflix.data.repository.CatalogRepository
import com.andrespar2.parflix.domain.MediaType
import com.startapp.sdk.ads.nativead.NativeAdDetails
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
 *
 * También administra los anuncios nativos de la pantalla: se piden solo los
 * que pueden mostrarse según las reglas de [AdsDistribution] y nunca se
 * reintenta en bucle cuando no hay inventario.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModel(
    private val catalogRepository: CatalogRepository,
    private val adsManager: AdsManager,
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

    /** Anuncios nativos ya cargados para esta pantalla (máximo 2). */
    private val _nativeAds = MutableStateFlow<List<NativeAdDetails>>(emptyList())
    val nativeAds: StateFlow<List<NativeAdDetails>> = _nativeAds.asStateFlow()

    private var nativeLoading = false

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

    /**
     * Pide anuncios nativos si el catálogo ya tiene contenido suficiente y aún
     * quedan ranuras. No bloquea la interfaz ni reintenta en bucle: si el
     * inventario falla, respeta el enfriamiento del SDK.
     */
    fun requestNativeAds(itemCount: Int) {
        if (nativeLoading) return

        val needed = AdsDistribution.maxAdsFor(itemCount)
        val loaded = _nativeAds.value.size
        if (loaded >= needed) return
        if (adsManager.shouldSkipNativeRetry()) return

        nativeLoading = true
        viewModelScope.launch {
            val ad = adsManager.loadNativeAd()
            if (ad != null) {
                _nativeAds.update { it + ad }
            }
            nativeLoading = false
        }
    }

    fun clearNativeAds() {
        _nativeAds.value.forEach { it.unregisterView() }
        _nativeAds.value = emptyList()
        nativeLoading = false
    }

    override fun onCleared() {
        // Libera la referencia de cada anuncio al salir de la pantalla.
        _nativeAds.value.forEach { it.unregisterView() }
        _nativeAds.value = emptyList()
    }

    companion object {
        private const val PAGE_SIZE = 60

        fun factory(container: AppContainer, mediaType: MediaType): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    CatalogViewModel(
                        catalogRepository = container.catalogRepository,
                        adsManager = container.adsManager,
                        mediaType = mediaType,
                    )
                }
            }
    }
}
