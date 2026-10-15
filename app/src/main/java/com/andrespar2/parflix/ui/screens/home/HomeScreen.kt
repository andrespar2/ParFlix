package com.andrespar2.parflix.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.andrespar2.parflix.core.util.ImageUrls
import com.andrespar2.parflix.data.local.entity.CatalogItemEntity
import com.andrespar2.parflix.domain.MediaType
import com.andrespar2.parflix.ui.components.EmptyState
import com.andrespar2.parflix.ui.components.MediaRow
import com.andrespar2.parflix.ui.components.OfflineBanner
import com.andrespar2.parflix.ui.components.SectionHeader
import com.andrespar2.parflix.ui.label
import com.andrespar2.parflix.ui.model.MediaCardData
import com.andrespar2.parflix.ui.model.toCard
import com.andrespar2.parflix.ui.rememberContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDetails: (MediaType, Int) -> Unit,
    onOpenCatalog: (MediaType) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val container = rememberContainer()
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))

    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val syncProgress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val highlights by viewModel.highlights.collectAsStateWithLifecycle(emptyList())
    val continueWatching by viewModel.continueWatching.collectAsStateWithLifecycle(emptyList())
    val movies by viewModel.recentMovies.collectAsStateWithLifecycle(emptyList())
    val series by viewModel.recentSeries.collectAsStateWithLifecycle(emptyList())
    val anime by viewModel.recentAnime.collectAsStateWithLifecycle(emptyList())
    val topRated by viewModel.topRated.collectAsStateWithLifecycle(emptyList())
    val total by viewModel.totalCount.collectAsStateWithLifecycle(0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "ParFlix",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "Buscar")
                    }
                    IconButton(onClick = onOpenLibrary) {
                        Icon(Icons.Filled.BookmarkBorder, contentDescription = "Mi lista")
                    }
                    IconButton(onClick = onOpenAbout) {
                        Icon(Icons.Filled.Info, contentDescription = "Acerca de")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ajustes")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val empty = total == 0
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (!isOnline) {
                item { OfflineBanner() }
            }

            if (syncProgress.running) {
                item {
                    SyncStatus(label = syncProgress.mediaType?.let { "Sincronizando ${it.label()}" }
                        ?: "Sincronizando catálogo")
                }
            }

            if (empty) {
                item {
                    EmptyState(
                        message = "Cargando el catálogo",
                        detail = "ParFlix está descargando títulos desde Vimeus. " +
                            "El contenido aparecerá en cuanto llegue la primera página.",
                    )
                }
                return@LazyColumn
            }

            if (highlights.isNotEmpty()) {
                item {
                    HeroPager(highlights) { item ->
                        onOpenDetails(item.type ?: MediaType.MOVIE, item.tmdbId)
                    }
                }
            }

            if (continueWatching.isNotEmpty()) {
                item {
                    MediaRow(
                        title = "Continuar viendo",
                        items = continueWatching.map { it.toCard() },
                        onItemClick = { onClickCard(it, onOpenDetails) },
                    )
                }
            }

            if (movies.isNotEmpty()) {
                item {
                    MediaRow(
                        title = "Películas",
                        items = movies.map { it.toCard() },
                        onItemClick = { onClickCard(it, onOpenDetails) },
                        onSeeAll = { onOpenCatalog(MediaType.MOVIE) },
                    )
                }
            }

            if (series.isNotEmpty()) {
                item {
                    MediaRow(
                        title = "Series",
                        items = series.map { it.toCard() },
                        onItemClick = { onClickCard(it, onOpenDetails) },
                        onSeeAll = { onOpenCatalog(MediaType.SERIES) },
                    )
                }
            }

            if (anime.isNotEmpty()) {
                item {
                    MediaRow(
                        title = "Anime",
                        items = anime.map { it.toCard() },
                        onItemClick = { onClickCard(it, onOpenDetails) },
                        onSeeAll = { onOpenCatalog(MediaType.ANIME) },
                    )
                }
            }

            if (topRated.isNotEmpty()) {
                item {
                    MediaRow(
                        title = "Mejor valoradas",
                        items = topRated.map { it.toCard() },
                        onItemClick = { onClickCard(it, onOpenDetails) },
                    )
                }
            }
        }
    }
}

private fun onClickCard(data: MediaCardData, onOpenDetails: (MediaType, Int) -> Unit) {
    val type = data.mediaType ?: return
    onOpenDetails(type, data.tmdbId)
}

@Composable
private fun SyncStatus(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Carrusel de destacados con arte panorámico y degradado hacia el negro. */
@Composable
private fun HeroPager(
    items: List<CatalogItemEntity>,
    onClick: (CatalogItemEntity) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { items.size })
    Column {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 11f),
        ) { page ->
            val item = items[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onClick(item) },
            ) {
                val backdrop = ImageUrls.backdrop(item.backdropPath, ImageUrls.BACKDROP_LARGE)
                if (backdrop != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(backdrop)
                            .crossfade(true)
                            .build(),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                                startY = 240f,
                            )
                        ),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val year = item.year
                        val rating = item.voteAverage
                        val meta = buildList {
                            if (year != null) add(year.toString())
                            if (rating != null) add(String.format(java.util.Locale.US, "%.1f", rating))
                        }.joinToString("  ·  ")
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
