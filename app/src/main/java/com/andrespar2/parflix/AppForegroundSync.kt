package com.andrespar2.parflix

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.andrespar2.parflix.data.repository.CatalogRepository
import com.andrespar2.parflix.data.sync.SyncScheduler
import com.andrespar2.parflix.data.preferences.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Actualización al abrir la app y al volver al primer plano.
 *
 * Es independiente de WorkManager: el trabajo periódico no es puntual en
 * Android (Doze, batería), por eso la app comprueba cambios también al
 * reanudarse y solo si el usuario lo permite.
 *
 * Nunca interrumpe la reproducción: no se usa esta vía dentro del reproductor
 * y el motor serializa las sincronizaciones con un mutex.
 */
class AppForegroundSync(
    private val catalogRepository: CatalogRepository,
    private val preferences: UserPreferences,
    private val syncScheduler: SyncScheduler,
    private val scope: CoroutineScope,
) : DefaultLifecycleObserver {

    override fun onResume(owner: LifecycleOwner) {
        scope.launch {
            val settings = preferences.settings.first()
            // Mantiene el trabajo periódico alineado con la preferencia.
            if (settings.periodicSync) syncScheduler.schedulePeriodic()
            if (settings.syncOnOpen) catalogRepository.syncOnOpen()
        }
    }
}
