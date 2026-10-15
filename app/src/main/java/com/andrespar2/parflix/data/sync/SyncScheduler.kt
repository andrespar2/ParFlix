package com.andrespar2.parflix.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Programación de trabajos periódicos con WorkManager.
 *
 * Honestidad sobre Android: los trabajos periódicos NO son puntuales; el
 * sistema los difiere según batería, Doze y uso. Por eso la actualización al
 * abrir la app o volver al primer plano es independiente y se hace siempre.
 */
class SyncScheduler(private val context: Context) {

    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<CatalogSyncWorker>(
            repeatInterval = REPEAT_HOURS,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
            flexTimeInterval = FLEX_MINUTES,
            flexTimeIntervalUnit = TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelPeriodic() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC_NAME)
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "parflix_catalog_sync"
        private const val REPEAT_HOURS = 6L
        private const val FLEX_MINUTES = 30L
    }
}
