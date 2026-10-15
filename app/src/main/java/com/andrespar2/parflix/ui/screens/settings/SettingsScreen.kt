package com.andrespar2.parflix.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andrespar2.parflix.BuildConfig
import com.andrespar2.parflix.ui.rememberContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val container = rememberContainer()
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))

    val settings by viewModel.settings.collectAsStateWithLifecycle(initialValue = null)
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val progress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val credentials by viewModel.credentialDescription.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        val value = message
        if (value != null) {
            snackbarHostState.showSnackbar(value)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenAbout) {
                        Icon(Icons.Filled.Info, contentDescription = "Acerca de")
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                SectionTitle("Sincronización")
            }
            item {
                SwitchRow(
                    title = "Actualizar al abrir la app",
                    subtitle = "Comprueba cambios al iniciar y al volver al primer plano.",
                    checked = settings?.syncOnOpen ?: true,
                    onCheckedChange = viewModel::setSyncOnOpen,
                )
            }
            item {
                SwitchRow(
                    title = "Actualización periódica",
                    subtitle = "Trabajo en segundo plano. Android decide el momento exacto " +
                        "según batería y uso: no es puntual.",
                    checked = settings?.periodicSync ?: true,
                    onCheckedChange = viewModel::setPeriodicSync,
                )
            }
            item {
                SwitchRow(
                    title = "Solo por Wi-Fi",
                    subtitle = "Preferencia conservadora para no gastar datos móviles.",
                    checked = settings?.wifiOnlySync ?: false,
                    onCheckedChange = viewModel::setWifiOnlySync,
                )
            }
            item {
                IntervalSelector(
                    current = settings?.fullScanIntervalDays ?: 7,
                    onSelect = viewModel::setFullScanIntervalDays,
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = viewModel::syncNow,
                        enabled = !progress.running,
                        modifier = Modifier.weight(1f),
                    ) { Text(if (progress.running) "Sincronizando…" else "Sincronizar ahora") }
                    OutlinedButton(
                        onClick = viewModel::fullReconcile,
                        enabled = !progress.running,
                        modifier = Modifier.weight(1f),
                    ) { Text("Reconciliar todo") }
                }
            }
            item {
                Text(
                    text = if (isOnline) "Con conexión" else "Sin conexión",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item { Divider() }
            item { SectionTitle("Proveedores y credenciales") }
            item {
                Text(
                    text = credentials.ifBlank { "Sin información" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item {
                Text(
                    text = "Las credenciales integradas en la APK son recuperables mediante " +
                        "ingeniería inversa. La ofuscación de compilación solo disuade la lectura " +
                        "casual; no es confidencialidad criptográfica. Las credenciales que " +
                        "introduzcas manualmente se guardan cifradas con Android Keystore.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            item { Divider() }
            item { SectionTitle("Almacenamiento local") }
            item {
                StatRow("Películas", stats.movies.toString())
                StatRow("Series", stats.series.toString())
                StatRow("Anime", stats.anime.toString())
                StatRow("Fichas enriquecidas", stats.enriched.toString())
                StatRow("Episodios en caché", stats.episodes.toString())
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = viewModel::clearCatalog,
                        modifier = Modifier.weight(1f),
                    ) { Text("Borrar catálogo") }
                    OutlinedButton(
                        onClick = viewModel::clearLibrary,
                        modifier = Modifier.weight(1f),
                    ) { Text("Borrar historial") }
                }
            }

            item { Divider() }
            item { SectionTitle("Acerca de") }
            item {
                StatRow("Versión", BuildConfig.VERSION_NAME)
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "ParFlix · Desarrollado por andrespar2",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onOpenAbout) { Text("Ver créditos") }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Divider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.outline,
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntervalSelector(current: Int, onSelect: (Int) -> Unit) {
    val options = listOf(1, 3, 7, 14, 30)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            text = "Reconciliación completa cada",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "Barrido íntegro que además elimina lo que ya no existe en el proveedor.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRowChips(options = options, current = current, onSelect = onSelect)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LazyRowChips(options: List<Int>, current: Int, onSelect: (Int) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier.padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options) { days ->
            FilterChip(
                selected = days == current,
                onClick = { onSelect(days) },
                label = { Text(if (days == 1) "1 día" else "$days días") },
            )
        }
    }
}
