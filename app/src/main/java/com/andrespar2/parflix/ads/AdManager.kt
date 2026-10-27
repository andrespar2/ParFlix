package com.andrespar2.parflix.ads

import android.content.Context
import android.util.Log
import androidx.lifecycle.ProcessLifecycleOwner
import com.andrespar2.parflix.core.network.NetworkMonitor
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Punto de entrada de la publicidad de ParFlix.
 *
 * No inicializa ningún SDK externo: cada placement tiene su propio
 * [AdController] y comparte el entorno de conectividad y ciclo de vida. Esto
 * permite que un espacio falle y se recupere sin afectar a los demás.
 */
class AdManager(
    context: Context,
    networkMonitor: NetworkMonitor,
) {

    private val appContext = context.applicationContext

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val environment = AndroidAdEnvironment(
        networkMonitor = networkMonitor,
        lifecycle = ProcessLifecycleOwner.get().lifecycle,
    )

    private val provider: AdProvider = AdsterraWebProvider()

    private val retryPolicy = AdRetryPolicy()

    private val controllers = ConcurrentHashMap<AdPlacement, AdController>()

    /** Estado de autorización vigente para las unidades de Android. */
    val authorization: AdAuthorization
        get() = AdConfiguration.androidAuthorization

    /**
     * Debe llamarse una sola vez desde [android.app.Application.onCreate].
     * No abre conexiones: solo deja registrado el estado de autorización.
     */
    fun init() {
        val authorization = AdConfiguration.androidAuthorization
        when (authorization) {
            is AdAuthorization.Authorized -> Log.i(
                TAG,
                "Publicidad Android autorizada para ${AdConfiguration.WEBSITE}",
            )

            is AdAuthorization.PendingWrittenConsent -> Log.i(
                TAG,
                "Publicidad Android en espera de autorización escrita; no se harán solicitudes",
            )

            is AdAuthorization.Disabled -> Log.i(
                TAG,
                "Publicidad Android deshabilitada por configuración",
            )
        }
        Log.d(TAG, "app=$appContext.packageName publisher=${AdConfiguration.PUBLISHER_NAME}")
    }

    /** Controlador único por ubicación (sin duplicar solicitudes al recomponer). */
    fun controller(placement: AdPlacement): AdController =
        controllers.computeIfAbsent(placement) {
            AdController(
                placement = placement,
                provider = provider,
                environment = environment,
                retryPolicy = retryPolicy,
                scope = scope,
            )
        }

    /** Libera el alcance de publicidad si el proceso lo requiere. */
    fun shutdown() {
        controllers.values.forEach { it.release() }
        controllers.clear()
        scope.cancel()
    }

    private companion object {
        const val TAG = "ParFlixAds"
    }
}
