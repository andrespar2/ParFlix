package com.andrespar2.parflix.ui.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.andrespar2.parflix.data.local.entity.EpisodeEntity
import com.andrespar2.parflix.domain.MediaDetailsView
import com.andrespar2.parflix.domain.MediaType
import com.andrespar2.parflix.ui.components.EmptyState
import com.andrespar2.parflix.ui.components.LoadingBox
import com.andrespar2.parflix.ui.components.RatingChip
import com.andrespar2.parflix.ui.rememberContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    mediaType: MediaType,
    tmdbId: Int,
    onBack: () -> Unit,
    onPlay: (mediaType: MediaType, tmdbId: Int, season: Int, episode: Int) -> Unit,
    onOpenDetails: (MediaType, Int) -> Unit,
) {
    val container = rememberContainer()
    val viewModel: DetailsViewModel = viewModel(
        key = "details-${mediaType.name}-$tmdbId",
        factory = DetailsViewModel.factory(container, mediaType, tmdbId),
    )

    val details by viewModel.details.collectAsStateWithLifecycle(null)
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle(false)
    val selectedSeason by viewModel.selectedSeason.collectAsStateWithLifecycle()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle(emptyList())
    val loadingEpisodes by viewModel.loadingEpisodes.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(message) {
        val value = message
        if (value != null) {
            snackbarHostState.showSnackbar(value)
            viewModel.consumeMessage()
        }
    }

    val view = details
    LaunchedEffect(view?.seasonCount) {
        if (view != null && view.mediaType.isShow) {
            val seasons = view.displaySeasons.map { it.number }
                .ifEmpty { (1..view.seasonCount.coerceAtLeast(1)).toList() }
            viewModel.onSeasonsKnown(seasons)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(view?.title ?: "Detalle", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            view == null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                LoadingBox()
            }

            else -> DetailsContent(
                view = view,
                padding = padding,
                isFavorite = isFavorite,
                selectedSeason = selectedSeason,
                episodes = episodes,
                loadingEpisodes = loadingEpisodes,
                onToggleFavorite = { viewModel.toggleFavorite(view.item) },
                onSelectSeason = viewModel::selectSeason,
                onPlayItem = {
                    viewModel.recordWatch(view.item, null, null, null)
                    onPlay(mediaType, tmdbId, -1, -1)
                },
                onPlayEpisode = { episode ->
                    viewModel.recordWatch(
                        item = view.item,
                        season = episode.season,
                        episode = episode.episode,
                        episodeTitle = episode.title,
                    )
                    onPlay(mediaType, tmdbId, episode.season, episode.episode)
                },
                onOpenTrailer = { key ->
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://www.youtube.com/watch?v=$key"),
                    )
                    runCatching { context.startActivity(intent) }
                },
                onOpenRecommendation = onOpenDetails,
            )
        }
    }
}

@Composable
private fun DetailsContent(
    view: MediaDetailsView,
    padding: PaddingValues,
    isFavorite: Boolean,
    selectedSeason: Int,
    episodes: List<EpisodeEntity>,
    loadingEpisodes: Boolean,
    onToggleFavorite: () -> Unit,
    onSelectSeason: (Int) -> Unit,
    onPlayItem: () -> Unit,
    onPlayEpisode: (EpisodeEntity) -> Unit,
    onOpenTrailer: (String) -> Unit,
    onOpenRecommendation: (MediaType, Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            Header(view = view)
        }

        item {
            Actions(
                view = view,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                onPlay = onPlayItem,
                onOpenTrailer = onOpenTrailer,
            )
        }

        val overview = view.overview
        if (!overview.isNullOrBlank()) {
            item {
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        if (view.mediaType.isShow) {
            item {
                SeasonsRow(
                    view = view,
                    selectedSeason = selectedSeason,
                    onSelect = onSelectSeason,
                )
            }
            if (loadingEpisodes && episodes.isEmpty()) {
                item { LoadingBox(modifier = Modifier.height(160.dp)) }
            } else if (episodes.isEmpty()) {
                item {
                    Text(
                        text = "Aún no hay episodios para esta temporada.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            } else {
                items(episodes, key = { "${it.season}:${it.episode}" }) { episode ->
                    EpisodeRow(episode = episode, onPlay = { onPlayEpisode(episode) })
                }
            }
        }

        if (view.cast.isNotEmpty()) {
            item {
                com.andrespar2.parflix.ui.components.SectionHeader(title = "Reparto")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(view.cast.take(20), key = { "${it.id}:${it.name}" }) { person ->
                        Column(
                            modifier = Modifier.width(88.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(40.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                val photo = ImageUrls.profile(person.photoPath)
                                if (photo != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(photo).crossfade(true).build(),
                                        contentDescription = person.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = person.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            person.role?.let { role ->
                                if (role.isNotBlank()) {
                                    Text(
                                        text = role,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (view.recommendations.isNotEmpty()) {
            item {
                com.andrespar2.parflix.ui.components.SectionHeader(title = "Recomendaciones")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(view.recommendations.take(20), key = { "${it.mediaType}:${it.tmdbId}" }) { rec ->
                        com.andrespar2.parflix.ui.components.PosterCard(
                            data = com.andrespar2.parflix.ui.model.MediaCardData(
                                tmdbId = rec.tmdbId,
                                mediaType = MediaType.fromString(rec.mediaType),
                                title = rec.title,
                                posterPath = rec.posterPath,
                                year = rec.year,
                                rating = rec.voteAverage,
                            ),
                            onClick = {
                                val type = MediaType.fromString(rec.mediaType) ?: view.mediaType
                                onOpenRecommendation(type, rec.tmdbId)
                            },
                        )
                    }
                }
            }
        }

        item {
            TechnicalInfo(view = view)
        }
    }
}

@Composable
private fun Header(view: MediaDetailsView) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
    ) {
        val backdrop = ImageUrls.backdrop(view.item.backdropPath ?: view.item.posterPath, ImageUrls.BACKDROP_LARGE)
        if (backdrop != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(backdrop).crossfade(true).build(),
                contentDescription = view.title,
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
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                        startY = 200f,
                    )
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            Text(
                text = view.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val rating = view.voteAverage
                if (rating != null && rating > 0) {
                    RatingChip(rating = rating)
                    Spacer(Modifier.width(10.dp))
                }
                val meta = buildList {
                    view.year?.let { add(it.toString()) }
                    if (view.mediaType.isShow && view.seasonCount > 0) {
                        add("${view.seasonCount} temporada(s)")
                    }
                }.joinToString("  ·  ")
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            if (view.genres.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = view.genres.joinToString(" · ") { it.name },
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Actions(
    view: MediaDetailsView,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPlay: () -> Unit,
    onOpenTrailer: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onPlay,
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Reproducir")
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                contentDescription = if (isFavorite) "Quitar de Mi lista" else "Añadir a Mi lista",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        val trailer = view.trailerKey
        if (trailer != null) {
            IconButton(onClick = { onOpenTrailer(trailer) }) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Ver tráiler",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeasonsRow(
    view: MediaDetailsView,
    selectedSeason: Int,
    onSelect: (Int) -> Unit,
) {
    val seasons = view.displaySeasons.map { it.number }
        .ifEmpty { (1..view.seasonCount.coerceAtLeast(1)).toList() }
    if (seasons.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(seasons, key = { it }) { number ->
            FilterChip(
                selected = number == selectedSeason,
                onClick = { onSelect(number) },
                label = { Text("Temporada $number") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

@Composable
private fun EpisodeRow(episode: EpisodeEntity, onPlay: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = episode.available, onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(68.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val still = ImageUrls.still(episode.stillPath)
            if (still != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(still).crossfade(true).build(),
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (!episode.available) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No disponible",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "${episode.episode}. ${episode.title ?: "Episodio ${episode.episode}"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val runtime = episode.runtimeMinutes
            val airDate = episode.airDate
            val meta = buildList {
                if (runtime != null && runtime > 0) add("$runtime min")
                if (!airDate.isNullOrBlank()) add(airDate)
            }.joinToString("  ·  ")
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (episode.available) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun TechnicalInfo(view: MediaDetailsView) {
    val rows = buildList {
        view.details?.status?.let { add("Estado" to it) }
        view.details?.runtimeMinutes?.takeIf { it > 0 }?.let { add("Duración" to "$it min") }
        view.details?.releaseDate?.takeIf { it.isNotBlank() }?.let { add("Estreno" to it) }
        view.details?.firstAirDate?.takeIf { it.isNotBlank() }?.let { add("Primera emisión" to it) }
        view.altTitles.firstOrNull()?.let { add("Título alternativo" to it) }
        view.companies.firstOrNull()?.let { add("Productora" to it.name) }
        view.countries.firstOrNull()?.let { add("País" to it.name) }
    }
    if (rows.isEmpty()) return
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        com.andrespar2.parflix.ui.components.SectionHeader(
            title = "Información",
            modifier = Modifier.padding(horizontal = 0.dp),
        )
        rows.forEach { (label, value) ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text(
                    text = "$label: ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}
