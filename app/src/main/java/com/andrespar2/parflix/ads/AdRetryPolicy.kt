package com.andrespar2.parflix.ads

import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Calcula esperas de reintento con backoff exponencial y jitter.
 *
 * Es una clase pura (sin Android, sin corrutinas) para poder verificarla con
 * pruebas unitarias deterministas. El controlador es quien duerme la espera.
 */
class AdRetryPolicy(
    private val retryDelaysMs: LongArray = AdConfiguration.RETRY_DELAYS_MS,
    private val noFillDelaysMs: LongArray = AdConfiguration.NO_FILL_DELAYS_MS,
    private val maxDelayMs: Long = AdConfiguration.RETRY_MAX_DELAY_MS,
    private val noFillMaxDelayMs: Long = AdConfiguration.NO_FILL_MAX_DELAY_MS,
    private val jitterRatio: Double = AdConfiguration.RETRY_JITTER_RATIO,
    private val random: Random = Random.Default,
) {

    /**
     * Evalúa un fallo. [attempt] empieza en 1 para el primer fallo.
     *
     * - Los errores de autorización o configuración no se reintentan.
     * - `Retry-After` tiene prioridad sobre el cálculo local.
     * - Sin inventario usa una tabla más lenta y separada.
     */
    fun onFailure(failure: AdFailure, attempt: Int): RetryDecision {
        if (!failure.isRecoverable) {
            return RetryDecision(
                shouldRetry = false,
                attempt = attempt,
                delayMs = 0L,
                failure = failure,
            )
        }

        if (failure.kind == AdFailureKind.NO_FILL) {
            return onNoFill(attempt)
        }

        val base = retryDelayFor(attempt)
        val jittered = applyJitter(base, maxDelayMs)
        val delay = failure.retryAfterMs
            ?.coerceAtLeast(MIN_RETRY_AFTER_MS)
            ?: jittered

        return RetryDecision(
            shouldRetry = true,
            attempt = attempt,
            delayMs = delay,
            failure = failure,
        )
    }

    /** Espera específica para falta de inventario. */
    fun onNoFill(attempt: Int): RetryDecision {
        val base = if (noFillDelaysMs.isEmpty()) {
            noFillMaxDelayMs
        } else {
            noFillDelaysMs[(attempt - 1).coerceIn(0, noFillDelaysMs.lastIndex)]
        }
        val delay = applyJitter(base, noFillMaxDelayMs)
        return RetryDecision(
            shouldRetry = true,
            attempt = attempt,
            delayMs = delay,
            failure = AdFailure(AdFailureKind.NO_FILL, "Sin inventario disponible"),
        )
    }

    /**
     * Espera base sin jitter: 5, 15, 30, 60 y 120 s; después se amplía hasta
     * el techo configurado en lugar de crecer sin límite.
     */
    fun retryDelayFor(attempt: Int): Long {
        if (retryDelaysMs.isEmpty()) return maxDelayMs
        val index = (attempt - 1).coerceAtLeast(0)
        if (index < retryDelaysMs.size) {
            return retryDelaysMs[index].coerceAtMost(maxDelayMs)
        }
        val extraPower = (index - retryDelaysMs.lastIndex).coerceAtMost(10)
        val grown = retryDelaysMs.last().toDouble() * (1 shl extraPower)
        return grown.roundToLong().coerceAtMost(maxDelayMs)
    }

    private fun applyJitter(base: Long, cap: Long): Long {
        if (jitterRatio <= 0.0) return base.coerceIn(MIN_DELAY_MS, cap)
        val factor = 1.0 + (random.nextDouble() * 2.0 - 1.0) * jitterRatio
        return (base * factor).roundToLong().coerceIn(MIN_DELAY_MS, cap)
    }

    private companion object {
        const val MIN_DELAY_MS = 1_000L
        const val MIN_RETRY_AFTER_MS = 1_000L
    }
}
