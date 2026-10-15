package com.andrespar2.parflix.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.parFlixDataStore: DataStore<Preferences> by preferencesDataStore(name = "parflix_settings")

/** Ajustes del usuario, guardados solo en este dispositivo. */
data class UserSettings(
    val syncOnOpen: Boolean = true,
    val periodicSync: Boolean = true,
    val enrichInBackground: Boolean = true,
    val wifiOnlySync: Boolean = false,
    val fullScanIntervalDays: Int = 7,
    val lastCatalogSyncAt: Long = 0,
    val syncPageBatch: Int = 5,
)

class UserPreferences(private val context: Context) {

    val settings: Flow<UserSettings> = context.parFlixDataStore.data.map { prefs ->
        UserSettings(
            syncOnOpen = prefs[KEY_SYNC_ON_OPEN] ?: true,
            periodicSync = prefs[KEY_PERIODIC_SYNC] ?: true,
            enrichInBackground = prefs[KEY_ENRICH_BACKGROUND] ?: true,
            wifiOnlySync = prefs[KEY_WIFI_ONLY] ?: false,
            fullScanIntervalDays = prefs[KEY_FULL_SCAN_DAYS] ?: 7,
            lastCatalogSyncAt = prefs[KEY_LAST_SYNC_AT] ?: 0,
            syncPageBatch = prefs[KEY_SYNC_PAGE_BATCH] ?: 5,
        )
    }

    suspend fun setSyncOnOpen(enabled: Boolean) = edit { it[KEY_SYNC_ON_OPEN] = enabled }

    suspend fun setPeriodicSync(enabled: Boolean) = edit { it[KEY_PERIODIC_SYNC] = enabled }

    suspend fun setEnrichInBackground(enabled: Boolean) = edit { it[KEY_ENRICH_BACKGROUND] = enabled }

    suspend fun setWifiOnlySync(enabled: Boolean) = edit { it[KEY_WIFI_ONLY] = enabled }

    suspend fun setFullScanIntervalDays(days: Int) =
        edit { it[KEY_FULL_SCAN_DAYS] = days.coerceIn(1, 30) }

    suspend fun setSyncPageBatch(pages: Int) = edit { it[KEY_SYNC_PAGE_BATCH] = pages.coerceIn(1, 81) }

    suspend fun setLastCatalogSyncAt(value: Long) = edit { it[KEY_LAST_SYNC_AT] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.parFlixDataStore.edit(block)
    }

    private companion object {
        val KEY_SYNC_ON_OPEN = booleanPreferencesKey("sync_on_open")
        val KEY_PERIODIC_SYNC = booleanPreferencesKey("periodic_sync")
        val KEY_ENRICH_BACKGROUND = booleanPreferencesKey("enrich_background")
        val KEY_WIFI_ONLY = booleanPreferencesKey("wifi_only_sync")
        val KEY_FULL_SCAN_DAYS = intPreferencesKey("full_scan_days")
        val KEY_LAST_SYNC_AT = longPreferencesKey("last_catalog_sync_at")
        val KEY_SYNC_PAGE_BATCH = intPreferencesKey("sync_page_batch")
    }
}
