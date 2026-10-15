package com.andrespar2.parflix.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.andrespar2.parflix.AppContainer
import kotlinx.coroutines.flow.first

/**
 * Sincronización periódica en segundo plano.
 *
 * - Respeta la preferencia del usuario.
 * - Decide entre verificación ligera y reconciliación completa según el tiempo
 *   transcurrido desde el último barrido íntegro.
 * - Devuelve `retry` ante fallos temporales de red (WorkManager aplicará su
 *   propio backoff) y `success` ante errores definitivos para no reintentar en vano.
 */
class CatalogSyncWorker(
    context: Context,
    params: WorkerParameters,
    private val container: AppContainer,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = container.preferences.settings.first()
        if (!settings.periodicSync) return Result.success()

        val synchronizer = container.catalogSynchronizer
        if (!container.networkMonitor.currentlyOnline()) return Result.retry()

        val dueForFullScan = synchronizer.isFullScanDue(settings.fullScanIntervalDays)
        val outcome = if (dueForFullScan) {
            synchronizer.fullReconcile()
        } else {
            synchronizer.syncOnOpen()
        }

        return when {
            outcome.success -> Result.success()
            outcome.error?.contains("Sin conexión", ignoreCase = true) == true -> Result.retry()
            outcome.error?.contains("ya en curso", ignoreCase = true) == true -> Result.success()
            else -> Result.retry()
        }
    }
}
