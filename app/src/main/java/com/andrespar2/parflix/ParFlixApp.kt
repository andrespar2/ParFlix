package com.andrespar2.parflix

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager

/**
 * Aplicación raíz de ParFlix.
 *
 * Mantiene el contenedor de dependencias (DI manual, sin frameworks externos)
 * y configura WorkManager con una fábrica propia.
 */
class ParFlixApp : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(ParFlixWorkerFactory(container))
            .build()

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
    }

    /** Programa (o reprograma) los trabajos periódicos según las preferencias. */
    fun ensurePeriodicSyncScheduled() {
        container.syncScheduler.schedulePeriodic()
    }

    companion object {
        lateinit var instance: ParFlixApp
            private set

        fun workManager(): WorkManager = WorkManager.getInstance(instance)
    }
}
