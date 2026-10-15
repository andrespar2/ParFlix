package com.andrespar2.parflix.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andrespar2.parflix.domain.MediaType
import com.andrespar2.parflix.ui.components.EmptyState
import com.andrespar2.parflix.ui.components.LoadingBox
import com.andrespar2.parflix.ui.components.MediaGrid
import com.andrespar2.parflix.ui.components.SectionHeader
import com.andrespar2.parflix.ui.model.toCard
import com.andrespar2.parflix.ui.rememberContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenDetails: (MediaType, Int) -> Unit,
) {
    val container = rememberContainer()
    val viewModel: SearchViewModel = viewModel(factory = SearchViewModel.factory(container))

    val state by viewModel.state.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle(emptyList())

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = viewModel::onQueryChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            placeholder = { Text("Buscar películas, series o anime") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = {
                                if (state.query.isNotEmpty()) {
                                    IconButton(onClick = viewModel::clearQuery) {
                                        Icon(Icons.Filled.Close, contentDescription = "Limpiar")
                                    }
                                }
                            },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                imeAction = ImeAction.Search,
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onSearch = { viewModel.submit(state.query) },
                            ),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
                FilterRow(selected = filter, onSelect = viewModel::setFilter)
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.loading -> LoadingBox()

                state.error != null -> EmptyState(
                    message = "No se pudo buscar",
                    detail = state.error,
                )

                state.query.trim().length < 2 -> RecentSearches(
                    recent = recent.map { it.query },
                    onPick = viewModel::selectSuggestion,
                    onClear = viewModel::clearRecent,
                )

                state.result.items.isEmpty() && state.result.remoteSuggestions.isEmpty() -> EmptyState(
                    message = "Sin resultados",
                    detail = "No se encontró \"${state.query}\" en el catálogo local. " +
                        "Prueba con otro título o revisa la ortografía.",
                )

                else -> Column {
                    if (state.result.usedFuzzy) {
                        Text(
                            text = "Resultados aproximados para \"${state.query}\"",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    if (state.result.items.isNotEmpty()) {
                        Text(
                            text = "${state.result.items.size} resultados en tu catálogo",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        MediaGrid(
                            items = state.result.items.map { it.toCard() },
                            onItemClick = { card ->
                                card.mediaType?.let { onOpenDetails(it, card.tmdbId) }
                            },
                            contentPadding = PaddingValues(16.dp),
                        )
                    } else if (state.result.remoteSuggestions.isNotEmpty()) {
                        LazyColumn {
                            item {
                                Text(
                                    text = "En TMDB pero aún no disponibles en el catálogo local",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                            items(state.result.remoteSuggestions, key = { "${it.mediaType}:${it.tmdbId}" }) { suggestion ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(suggestion.title, style = MaterialTheme.typography.bodyLarge)
                                        val meta = buildList {
                                            suggestion.year?.let { add(it.toString()) }
                                            suggestion.mediaType.let { add(it) }
                                        }.joinToString("  ·  ")
                                        Text(
                                            meta,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(selected: MediaType?, onSelect: (MediaType?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("Todo") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
        items(MediaType.entries.toList(), key = { it.name }) { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelect(type) },
                label = {
                    Text(
                        when (type) {
                            MediaType.MOVIE -> "Películas"
                            MediaType.SERIES -> "Series"
                            MediaType.ANIME -> "Anime"
                        }
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

@Composable
private fun RecentSearches(
    recent: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (recent.isEmpty()) {
        EmptyState(
            message = "Busca en tu catálogo",
            detail = "Escribe un título, un año o un identificador (tt1234567). " +
                "La búsqueda funciona aunque estés sin conexión.",
        )
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = "Búsquedas recientes", modifier = Modifier.weight(1f))
            TextButton(onClick = onClear) { Text("Borrar") }
        }
        LazyColumn {
            items(recent, key = { it }) { query ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(query) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(0.dp))
                    Text(
                        text = query,
                        modifier = Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
