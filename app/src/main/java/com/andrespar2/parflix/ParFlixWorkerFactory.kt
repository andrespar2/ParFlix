package com.andrespar2.parflix

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.andrespar2.parflix.data.sync.CatalogSyncWorker

/** Fábrica de trabajadores que inyecta el contenedor de dependencias. */
class ParFlixWorkerFactory(private val container: AppContainer) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        CatalogSyncWorker::class.java.name -> CatalogSyncWorker(appContext, workerParameters, container)
        else -> null
    }
}
