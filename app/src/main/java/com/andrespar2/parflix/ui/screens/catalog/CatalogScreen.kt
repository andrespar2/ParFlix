package com.andrespar2.parflix.ui.screens.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andrespar2.parflix.domain.MediaType
import com.andrespar2.parflix.ui.components.EmptyState
import com.andrespar2.parflix.ui.components.ParFlixNativeAdCard
import com.andrespar2.parflix.ui.components.PosterCard
import com.andrespar2.parflix.ui.model.toCard
import com.andrespar2.parflix.ui.rememberContainer
import com.andrespar2.parflix.ui.title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    mediaType: MediaType,
    onBack: () -> Unit,
    onOpenDetails: (MediaType, Int) -> Unit,
) {
    val container = rememberContainer()
    val viewModel: CatalogViewModel = viewModel(
        key = "catalog-${mediaType.name}",
        factory = CatalogViewModel.factory(container, mediaType),
    )

    val items by viewModel.items.collectAsStateWithLifecycle(emptyList())
    val genres by viewModel.genres.collectAsStateWithLifecycle(emptyList())
    val selectedGenre by viewModel.selectedGenre.collectAsStateWithLifecycle()
    val total by viewModel.totalCount.collectAsStateWithLifecycle(0)
    val nativeAds by viewModel.nativeAds.collectAsStateWithLifecycle(emptyList())

    val gridState = rememberLazyGridState()

    // Intercala los anuncios nativos ya cargados entre las tarjetas reales.
    // Las claves de las tarjetas se mantienen estables, así que insertar
    // publicidad no recompone ni reinicia la posición del contenido.
    val cells = remember(items, nativeAds) { buildCatalogCells(items, nativeAds) }

    // Pide anuncios nativos solo cuando hay contenido suficiente para las
    // ranuras disponibles. Al cambiar el número de anuncios cargados se vuelve
    // a evaluar (permite cargar el segundo sin bucles: el ViewModel y
    // AdsManager aplican el enfriamiento y el tope por pantalla).
    LaunchedEffect(items.size, nativeAds.size) {
        viewModel.requestNativeAds(items.size)
    }

    // Paginación local: al acercarse al final se amplía la ventana visible.
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= items.size - 12
        }
    }
    LaunchedEffect(gridState) {
        snapshotFlow { shouldLoadMore }.collect { if (it) viewModel.loadMore() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(mediaType.title()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                text = "$total títulos disponibles sin conexión",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            if (genres.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        GenreChip(
                            label = "Todas",
                            selected = selectedGenre == null,
                            onClick = { viewModel.selectGenre(null) },
                        )
                    }
                    items(genres, key = { it.genreId }) { genre ->
                        GenreChip(
                            label = genre.name,
                            selected = selectedGenre == genre.genreId,
                            onClick = { viewModel.selectGenre(genre.genreId) },
                        )
                    }
                }
            }

            if (items.isEmpty()) {
                EmptyState(
                    message = "Sin resultados todavía",
                    detail = "La sincronización del catálogo está en curso. " +
                        "Desliza para actualizar o revisa tu conexión.",
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(
                        items = cells,
                        key = { it.key },
                        span = { cell ->
                            // El anuncio ocupa toda la fila para no romper la
                            // cuadrícula de portadas; el contenido sigue igual.
                            if (cell is CatalogCell.Sponsored) {
                                GridItemSpan(maxLineSpan)
                            } else {
                                GridItemSpan(1)
                            }
                        },
                    ) { cell ->
                        when (cell) {
                            is CatalogCell.Content -> PosterCard(
                                data = cell.item.toCard(),
                                onClick = { onOpenDetails(mediaType, cell.item.tmdbId) },
                                modifier = Modifier.fillMaxWidth(),
                            )

                            is CatalogCell.Sponsored -> ParFlixNativeAdCard(
                                ad = cell.ad,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                                .padding(start = 48.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenreChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}
