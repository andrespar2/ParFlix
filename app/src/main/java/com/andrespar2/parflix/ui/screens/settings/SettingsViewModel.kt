package com.andrespar2.parflix.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.andrespar2.parflix.AppContainer
import com.andrespar2.parflix.core.network.NetworkMonitor
import com.andrespar2.parflix.core.security.CredentialStore
import com.andrespar2.parflix.data.preferences.UserPreferences
import com.andrespar2.parflix.data.preferences.UserSettings
import com.andrespar2.parflix.data.repository.CatalogRepository
import com.andrespar2.parflix.data.repository.LibraryRepository
import com.andrespar2.parflix.data.repository.LocalStats
import com.andrespar2.parflix.data.sync.SyncProgress
import com.andrespar2.parflix.data.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Ajustes, estadísticas locales y control manual de la sincronización. */
class SettingsViewModel(
    private val preferences: UserPreferences,
    private val catalogRepository: CatalogRepository,
    private val libraryRepository: LibraryRepository,
    private val credentialStore: CredentialStore,
    private val syncScheduler: SyncScheduler,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    val settings: Flow<UserSettings> = preferences.settings
    val syncProgress: StateFlow<SyncProgress> = catalogRepository.syncProgress
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

    private val _stats = MutableStateFlow(LocalStats())
    val stats: StateFlow<LocalStats> = _stats.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _credentialDescription = MutableStateFlow("")
    val credentialDescription: StateFlow<String> = _credentialDescription.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _stats.value = catalogRepository.localStats()
            _credentialDescription.value = credentialStore.describe()
        }
    }

    fun setSyncOnOpen(enabled: Boolean) = launch { preferences.setSyncOnOpen(enabled) }

    fun setPeriodicSync(enabled: Boolean) = launch {
        preferences.setPeriodicSync(enabled)
        if (enabled) syncScheduler.schedulePeriodic() else syncScheduler.cancelPeriodic()
    }

    fun setWifiOnlySync(enabled: Boolean) = launch { preferences.setWifiOnlySync(enabled) }

    fun setFullScanIntervalDays(days: Int) = launch { preferences.setFullScanIntervalDays(days) }

    fun syncNow() = launch {
        _message.value = "Sincronizando…"
        val outcome = catalogRepository.syncOnOpen()
        _message.value = if (outcome.success) {
            "Sincronización completada: +${outcome.added} · ~${outcome.updated}"
        } else {
            "No se pudo sincronizar: ${outcome.error ?: "error desconocido"}"
        }
        refresh()
    }

    fun fullReconcile() = launch {
        _message.value = "Reconciliación completa en curso…"
        val outcome = catalogRepository.fullReconcile()
        _message.value = if (outcome.success) {
            "Reconciliación completada: ${outcome.removed} eliminados"
        } else {
            "No se pudo reconciliar: ${outcome.error ?: "error desconocido"}"
        }
        refresh()
    }

    fun clearCatalog() = launch {
        catalogRepository.clearCatalog()
        _message.value = "Catálogo local borrado"
        refresh()
    }

    fun clearLibrary() = launch {
        libraryRepository.clearLibrary()
        _message.value = "Historial y búsquedas borrados"
        refresh()
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                SettingsViewModel(
                    preferences = container.preferences,
                    catalogRepository = container.catalogRepository,
                    libraryRepository = container.libraryRepository,
                    credentialStore = container.credentialStore,
                    syncScheduler = container.syncScheduler,
                    networkMonitor = container.networkMonitor,
                )
            }
        }
    }
}
