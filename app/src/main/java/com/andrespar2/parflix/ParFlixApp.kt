package com.andrespar2.parflix

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Aplicación raíz de ParFlix.
 *
 * Mantiene el contenedor de dependencias (DI manual, sin frameworks externos),
 * configura WorkManager con una fábrica propia y registra la actualización al
 * volver al primer plano.
 */
class ParFlixApp : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(ParFlixWorkerFactory(container))
            .build()

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)

        // Start.io se inicializa una sola vez, sin ads de retorno ni formatos
        // automáticos. Ver AdsConfig para identificadores y frecuencias.
        container.adsManager.init()

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            AppForegroundSync(
                catalogRepository = container.catalogRepository,
                preferences = container.preferences,
                syncScheduler = container.syncScheduler,
                scope = appScope,
            )
        )

        // El trabajo periódico se mantiene solo si el usuario lo permite.
        appScope.launch {
            if (container.preferences.settings.first().periodicSync) {
                container.syncScheduler.schedulePeriodic()
            }
        }
    }

    companion object {
        lateinit var instance: ParFlixApp
            private set

        fun workManager(): WorkManager = WorkManager.getInstance(instance)
    }
}
