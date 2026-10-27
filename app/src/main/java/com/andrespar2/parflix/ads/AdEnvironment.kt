package com.andrespar2.parflix.ads

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.andrespar2.parflix.core.network.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Entorno observable del que depende un espacio publicitario.
 *
 * Se abstrae en una interfaz para poder simular conectividad, primer plano y
 * reloj en las pruebas sin Android.
 */
interface AdEnvironment {
    val isOnline: StateFlow<Boolean>
    val isForeground: StateFlow<Boolean>

    fun nowMillis(): Long
}

/**
 * Implementación real: reutiliza [NetworkMonitor] (una sola suscripción para
 * toda la app) y el ciclo de vida del proceso.
 */
class AndroidAdEnvironment(
    networkMonitor: NetworkMonitor,
    lifecycle: Lifecycle,
) : AdEnvironment {

    private val connectivity = AdConnectivityObserver(networkMonitor)
    private val lifecycleObserver = AdLifecycleObserver(lifecycle)

    override val isOnline: StateFlow<Boolean> = connectivity.isOnline
    override val isForeground: StateFlow<Boolean> = lifecycleObserver.isForeground

    override fun nowMillis(): Long = SystemClock.elapsedRealtime()
}

/** Observa el ciclo de vida y evita solicitudes mientras la app está oculta. */
class AdLifecycleObserver(lifecycle: Lifecycle) {
    private val _isForeground = MutableStateFlow(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                _isForeground.value = true
            }

            override fun onStop(owner: LifecycleOwner) {
                _isForeground.value = false
            }
        })
    }
}

/** Envuelve la conectividad compartida sin abrir otra suscripción al sistema. */
class AdConnectivityObserver(networkMonitor: NetworkMonitor) {
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
}
