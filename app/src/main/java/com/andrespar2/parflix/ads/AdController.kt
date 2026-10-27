package com.andrespar2.parflix.ads

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Controlador de un único espacio publicitario.
 *
 * Garantías:
 *  - Máximo una solicitud en curso por placement.
 *  - Un fallo recuperable nunca desactiva el espacio: se reprograma con
 *    backoff, respetando `Retry-After`, la conectividad y el primer plano.
 *  - Un error de autorización o configuración termina el ciclo sin reintentos.
 *  - Al salir de la pantalla se cancelan los intentos; el estado Ready/Visible
 *    se conserva para reutilizarlo sin recargar.
 *  - Ninguna corrutina duerme en el hilo principal: [delay] es suspendido.
 */
class AdController(
    val placement: AdPlacement,
    private val provider: AdProvider,
    private val environment: AdEnvironment,
    private val retryPolicy: AdRetryPolicy,
    private val scope: CoroutineScope,
    private val timeoutMs: Long = AdConfiguration.LOAD_TIMEOUT_MS,
    private val freshnessMs: Long = AdConfiguration.CREATIVE_FRESHNESS_MS,
) {

    private val _state = MutableStateFlow<AdLoadState>(AdLoadState.Idle)
    val state: StateFlow<AdLoadState> = _state.asStateFlow()

    private val lock = Any()

    @Volatile
    private var consumers = 0

    @Volatile
    private var loadJob: Job? = null

    @Volatile
    private var watchJob: Job? = null

    @Volatile
    private var attempt = 0

    @Volatile
    private var nextAttemptAt = 0L

    @Volatile
    private var readyAt = 0L

    /** La pantalla que contiene el espacio empieza a necesitarlo. */
    fun acquire() {
        val start = synchronized(lock) {
            consumers += 1
            if (consumers == 1) {
                invalidateIfStale()
                true
            } else {
                false
            }
        }
        if (start) startWatchingEnvironment()
    }

    /** La pantalla dejó de necesitarlo: se cancelan cargas y esperas. */
    fun release() {
        val stop = synchronized(lock) {
            if (consumers > 0) consumers -= 1
            consumers == 0
        }
        if (!stop) return

        loadJob?.cancel()
        loadJob = null
        watchJob?.cancel()
        watchJob = null

        // Una petición interrumpida no es un fallo: al volver se reintenta.
        if (_state.value is AdLoadState.Loading) {
            _state.value = AdLoadState.Idle
        }
    }

    /** Marca que el contenedor ya está en pantalla. No genera impresiones. */
    fun markVisible() {
        val current = _state.value
        if (current is AdLoadState.Ready) {
            _state.value = AdLoadState.Visible(current.creative)
        }
    }

    /**
     * La WebView no pudo representar la creatividad. Se trata como un fallo
     * recuperable y entra en el mismo ciclo de backoff.
     */
    fun onCreativeFailure(failure: AdFailure) {
        synchronized(lock) {
            if (consumers == 0) return
        }
        _state.value = AdLoadState.Idle
        if (!registerFailure(failure)) return
        schedule(force = true)
    }

    // ---------------------------------------------------------------------
    // Entorno y programación
    // ---------------------------------------------------------------------

    private fun startWatchingEnvironment() {
        watchJob = scope.launch {
            combine(
                environment.isOnline,
                environment.isForeground,
            ) { online, foreground -> online to foreground }
                .distinctUntilChanged()
                .collect { (online, foreground) ->
                    onEnvironmentChanged(online, foreground)
                }
        }
    }

    private fun onEnvironmentChanged(online: Boolean, foreground: Boolean) {
        synchronized(lock) {
            if (consumers == 0) return
        }

        if (!foreground) {
            cancelCurrentWork()
            _state.value = AdLoadState.Paused(AdPauseReason.APP_IN_BACKGROUND)
            return
        }
        if (!online) {
            cancelCurrentWork()
            _state.value = AdLoadState.Paused(AdPauseReason.OFFLINE)
            return
        }

        when (_state.value) {
            is AdLoadState.Idle,
            is AdLoadState.Paused,
            is AdLoadState.WaitingRetry,
            -> schedule()

            else -> Unit
        }
    }

    private fun cancelCurrentWork() {
        loadJob?.cancel()
        loadJob = null
    }

    private fun schedule(force: Boolean = false) {
        synchronized(lock) {
            if (consumers == 0) return
            if (loadJob?.isActive == true) return

            when (val authorization = provider.authorization(placement)) {
                is AdAuthorization.Authorized -> Unit
                is AdAuthorization.PendingWrittenConsent -> {
                    _state.value = AdLoadState.Unauthorized(authorization.reason)
                    return
                }

                is AdAuthorization.Disabled -> {
                    _state.value = AdLoadState.Disabled(authorization.reason)
                    return
                }
            }

            if (!environment.isForeground.value) {
                _state.value = AdLoadState.Paused(AdPauseReason.APP_IN_BACKGROUND)
                return
            }
            if (!environment.isOnline.value) {
                _state.value = AdLoadState.Paused(AdPauseReason.OFFLINE)
                return
            }

            val current = _state.value
            if (!force && (current is AdLoadState.Ready || current is AdLoadState.Visible)) {
                if (isFresh(current)) return
                invalidateIfStale(force = true)
            }

            loadJob = scope.launch { runLoadLoop() }
        }
    }

    private suspend fun runLoadLoop() {
        while (currentCoroutineContext().isActive) {
            if (consumers == 0) return

            if (!environment.isForeground.value) {
                _state.value = AdLoadState.Paused(AdPauseReason.APP_IN_BACKGROUND)
                return
            }
            if (!environment.isOnline.value) {
                _state.value = AdLoadState.Paused(AdPauseReason.OFFLINE)
                return
            }

            val wait = nextAttemptAt - environment.nowMillis()
            if (wait > 0L) {
                delay(wait)
                if (!currentCoroutineContext().isActive) return
            }

            _state.value = AdLoadState.Loading
            when (val result = executeLoad()) {
                is AdResult.Success -> {
                    attempt = 0
                    nextAttemptAt = 0L
                    readyAt = environment.nowMillis()
                    _state.value = AdLoadState.Ready(result.creative)
                    return
                }

                is AdResult.NoFill -> {
                    if (!registerFailure(AdFailure(AdFailureKind.NO_FILL, "Sin inventario"))) {
                        return
                    }
                }

                is AdResult.Failure -> {
                    if (!registerFailure(result.failure)) return
                }
            }
        }
    }

    private suspend fun executeLoad(): AdResult = try {
        withTimeout(timeoutMs) {
            provider.load(AdRequest(placement))
        }
    } catch (timeout: TimeoutCancellationException) {
        AdResult.Failure(
            AdFailure(AdFailureKind.TIMEOUT, "La solicitud publicitaria agotó el tiempo"),
        )
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        AdResult.Failure(
            AdFailure(AdFailureKind.NETWORK, "No se pudo completar la solicitud"),
        )
    }

    /**
     * Registra un fallo y programa el siguiente intento. Devuelve `false` si el
     * error es definitivo y no debe reintentarse.
     */
    private fun registerFailure(failure: AdFailure): Boolean {
        attempt += 1
        val decision = retryPolicy.onFailure(failure, attempt)
        if (!decision.shouldRetry) {
            _state.value = terminalState(failure)
            return false
        }

        nextAttemptAt = environment.nowMillis() + decision.delayMs
        _state.value = AdLoadState.WaitingRetry(
            attempt = decision.attempt,
            delayMs = decision.delayMs,
            failure = failure,
        )
        return true
    }

    private fun terminalState(failure: AdFailure): AdLoadState = when (failure.kind) {
        AdFailureKind.UNAUTHORIZED -> AdLoadState.Unauthorized(
            failure.message ?: "Unidad no autorizada",
        )

        AdFailureKind.CONFIGURATION -> AdLoadState.Disabled(
            failure.message ?: "Configuración publicitaria inválida",
        )

        else -> AdLoadState.Unrecoverable(failure)
    }

    private fun isFresh(state: AdLoadState): Boolean =
        state is AdLoadState.Ready || state is AdLoadState.Visible

    private fun invalidateIfStale(force: Boolean = false) {
        val current = _state.value
        if (current !is AdLoadState.Ready && current !is AdLoadState.Visible) return
        if (!force && environment.nowMillis() - readyAt <= freshnessMs) return

        _state.value = AdLoadState.Idle
        attempt = 0
        nextAttemptAt = 0L
    }
}
