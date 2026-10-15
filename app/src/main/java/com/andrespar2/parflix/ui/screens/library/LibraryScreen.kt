package com.andrespar2.parflix.ui.screens.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andrespar2.parflix.domain.MediaType
import com.andrespar2.parflix.ui.components.EmptyState
import com.andrespar2.parflix.ui.components.PosterCard
import com.andrespar2.parflix.ui.model.toCard
import com.andrespar2.parflix.ui.rememberContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onBack: () -> Unit,
    onOpenDetails: (MediaType, Int) -> Unit,
) {
    val container = rememberContainer()
    val viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container))

    val favorites by viewModel.favorites.collectAsStateWithLifecycle(emptyList())
    val continueWatching by viewModel.continueWatching.collectAsStateWithLifecycle(emptyList())

    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Mi lista") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    actions = {
                        if (selectedTab == 1 && continueWatching.isNotEmpty()) {
                            IconButton(onClick = viewModel::clearHistory) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Borrar historial",
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
                TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.background) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Favoritos (${favorites.size})") },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Continuar viendo (${continueWatching.size})") },
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (selectedTab == 0) {
                if (favorites.isEmpty()) {
                    EmptyState(
                        message = "Tu lista está vacía",
                        detail = "Añade títulos con el marcador desde la ficha de detalle.",
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 110.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(favorites, key = { "${it.mediaType}:${it.tmdbId}" }) { favorite ->
                            PosterCard(
                                data = favorite.toCard(),
                                onClick = {
                                    favorite.mediaType
                                        .let { MediaType.fromString(it) }
                                        ?.let { onOpenDetails(it, favorite.tmdbId) }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            } else {
                if (continueWatching.isEmpty()) {
                    EmptyState(
                        message = "Nada que continuar",
                        detail = "Los títulos que reproduzcas aparecerán aquí. " +
                            "No se inventa progreso: solo se guarda el último episodio abierto.",
                    )
                } else {
                    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(continueWatching, key = { "${it.mediaType}:${it.tmdbId}" }) { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PosterCard(
                                    data = entry.toCard(),
                                    onClick = {
                                        MediaType.fromString(entry.mediaType)
                                            ?.let { onOpenDetails(it, entry.tmdbId) }
                                    },
                                    modifier = Modifier.width(110.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = entry.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                    val season = entry.lastSeason
                                    val episode = entry.lastEpisode
                                    if (season != null && episode != null) {
                                        Text(
                                            text = "T$season E$episode" +
                                                (entry.lastEpisodeTitle?.let { " · $it" } ?: ""),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                IconButton(onClick = {
                                    MediaType.fromString(entry.mediaType)?.let {
                                        viewModel.removeFromHistory(entry.tmdbId, it)
                                    }
                                }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Quitar del historial",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
