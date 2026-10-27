package com.andrespar2.parflix.ads

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifica el backoff, el jitter y el respeto a Retry-After. */
class AdRetryPolicyTest {

    private fun policy(random: Random = Random(7)) = AdRetryPolicy(
        retryDelaysMs = longArrayOf(5_000, 15_000, 30_000, 60_000, 120_000),
        noFillDelaysMs = longArrayOf(120_000, 300_000, 600_000),
        maxDelayMs = 300_000,
        noFillMaxDelayMs = 600_000,
        jitterRatio = 0.0,
        random = random,
    )

    @Test
    fun `la secuencia inicial es cinco quince treinta sesenta ciento veinte`() {
        val policy = policy()
        assertEquals(
            listOf(5_000L, 15_000L, 30_000L, 60_000L, 120_000L),
            (1..5).map { policy.retryDelayFor(it) },
        )
    }

    @Test
    fun `despues de la tabla la espera crece hasta el techo`() {
        val policy = policy()
        assertEquals(240_000L, policy.retryDelayFor(6))
        assertEquals(300_000L, policy.retryDelayFor(7))
        assertEquals(300_000L, policy.retryDelayFor(50))
    }

    @Test
    fun `un error no recuperable no se reintenta`() {
        val decision = policy().onFailure(
            AdFailure(AdFailureKind.UNAUTHORIZED, "sin autorización"),
            attempt = 1,
        )
        assertFalse(decision.shouldRetry)
        assertEquals(0L, decision.delayMs)
    }

    @Test
    fun `retry after del proveedor tiene prioridad`() {
        val decision = policy().onFailure(
            AdFailure(
                AdFailureKind.RATE_LIMITED,
                "429",
                httpCode = 429,
                retryAfterMs = 90_000,
            ),
            attempt = 1,
        )
        assertTrue(decision.shouldRetry)
        assertEquals(90_000L, decision.delayMs)
    }

    @Test
    fun `el jitter se mantiene dentro del veinte por ciento`() {
        val random = Random(1234)
        val policy = AdRetryPolicy(jitterRatio = 0.2, random = random)
        repeat(200) {
            val delay = policy.retryDelayFor(1) // base 5 s
            assertTrue("delay=$delay", delay in 4_000L..6_000L)
        }
    }

    @Test
    fun `sin inventario usa una espera lenta y separada`() {
        val policy = policy()
        assertEquals(120_000L, policy.onNoFill(1).delayMs)
        assertEquals(300_000L, policy.onNoFill(2).delayMs)
        assertEquals(600_000L, policy.onNoFill(3).delayMs)
        assertEquals(600_000L, policy.onNoFill(10).delayMs)
    }

    @Test
    fun `los errores http transitorios se reintentan y los definitivos no`() {
        assertTrue(AdFailure.fromHttp(500).isRecoverable)
        assertTrue(AdFailure.fromHttp(503, 30_000).isRecoverable)
        assertTrue(AdFailure.fromHttp(429, 10_000).isRecoverable)
        assertFalse(AdFailure.fromHttp(401).isRecoverable)
        assertFalse(AdFailure.fromHttp(403).isRecoverable)
        assertEquals(AdFailureKind.NO_FILL, AdFailure.fromHttp(204).kind)
    }
}
