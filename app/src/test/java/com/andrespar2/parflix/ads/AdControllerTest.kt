package com.andrespar2.parflix.ads

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de la recuperación automática.
 *
 * Utilizan un proveedor simulado: nunca se solicitan anuncios reales ni se
 * generan impresiones.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AdControllerTest {

    private val creative = AdCreative.WebTag("<html></html>", AdFormat.BANNER_320X50)

    @Test
    fun `caso A un fallo inicial se reintenta y se recupera sin reiniciar`() = runTest {
        val provider = FakeProvider(
            results = ArrayDeque(
                listOf(
                    AdResult.Failure(AdFailure(AdFailureKind.NETWORK, "sin red temporal")),
                    AdResult.Success(creative),
                ),
            ),
        )
        val env = TestEnvironment(this)
        val controller = controller(provider, env)

        controller.acquire()
        advanceUntilIdle()

        assertTrue(controller.state.value is AdLoadState.Ready)
        assertEquals(2, provider.calls.size)
        assertTrue(provider.calls[1] - provider.calls[0] >= 5_000L)
    }

    @Test
    fun `caso B sin conexion no se solicita y al volver se recupera`() = runTest {
        val provider = FakeProvider(results = ArrayDeque(listOf(AdResult.Success(creative))))
        val env = TestEnvironment(this, online = false)
        val controller = controller(provider, env)

        controller.acquire()
        advanceUntilIdle()

        assertTrue(controller.state.value is AdLoadState.Paused)
        assertEquals(0, provider.calls.size)

        env.isOnline.value = true
        advanceUntilIdle()

        assertTrue(controller.state.value is AdLoadState.Ready)
        assertEquals(1, provider.calls.size)
    }

    @Test
    fun `caso C un timeout no bloquea y se reintenta`() = runTest {
        val provider = FakeProvider(
            results = ArrayDeque(listOf(AdResult.Success(creative))),
            hangFirst = true,
        )
        val env = TestEnvironment(this)
        val controller = controller(provider, env)

        controller.acquire()
        advanceTimeBy(1_500L)
        runCurrent()

        assertTrue(controller.state.value is AdLoadState.WaitingRetry)
        assertEquals(1, provider.calls.size)

        advanceTimeBy(6_000L)
        runCurrent()

        assertTrue(controller.state.value is AdLoadState.Ready)
        assertEquals(2, provider.calls.size)
    }

    @Test
    fun `caso D varios fallos aumentan la espera y nunca hay dos solicitudes a la vez`() = runTest {
        val provider = FakeProvider(
            results = ArrayDeque(
                listOf(
                    AdResult.Failure(AdFailure(AdFailureKind.NETWORK)),
                    AdResult.Failure(AdFailure(AdFailureKind.NETWORK)),
                    AdResult.Failure(AdFailure(AdFailureKind.NETWORK)),
                    AdResult.Success(creative),
                ),
            ),
            latencyMs = 100L,
        )
        val env = TestEnvironment(this)
        val controller = controller(provider, env)

        controller.acquire()
        advanceUntilIdle()

        assertTrue(controller.state.value is AdLoadState.Ready)
        assertEquals(4, provider.calls.size)
        assertEquals(1, provider.maxConcurrent)

        val gaps = provider.calls.zipWithNext { a, b -> b - a }
        assertTrue("gaps=$gaps", gaps[0] >= 5_000L)
        assertTrue("gaps=$gaps", gaps[1] >= 15_000L)
        assertTrue("gaps=$gaps", gaps[2] >= 30_000L)
    }

    @Test
    fun `caso E un anuncio exitoso no se recarga solo`() = runTest {
        val provider = FakeProvider(results = ArrayDeque(listOf(AdResult.Success(creative))))
        val env = TestEnvironment(this)
        val controller = controller(provider, env)

        controller.acquire()
        advanceUntilIdle()
        assertEquals(1, provider.calls.size)

        advanceTimeBy(3_600_000L)
        runCurrent()
        assertEquals(1, provider.calls.size)
        assertTrue(controller.state.value is AdLoadState.Ready)
    }

    @Test
    fun `caso F al salir de la pantalla se cancelan los intentos`() = runTest {
        val provider = FakeProvider(
            results = ArrayDeque(
                List(20) { AdResult.Failure(AdFailure(AdFailureKind.NETWORK)) },
            ),
        )
        val env = TestEnvironment(this)
        val controller = controller(provider, env)

        controller.acquire()
        advanceTimeBy(7_000L)
        runCurrent()
        val callsBeforeRelease = provider.calls.size
        assertTrue(callsBeforeRelease >= 1)

        controller.release()
        advanceTimeBy(900_000L)
        runCurrent()

        assertEquals(callsBeforeRelease, provider.calls.size)
    }

    @Test
    fun `caso G en segundo plano se pausan las solicitudes y se reanudan al volver`() = runTest {
        val provider = FakeProvider(
            results = ArrayDeque(
                List(20) { AdResult.Failure(AdFailure(AdFailureKind.NETWORK)) },
            ),
        )
        val env = TestEnvironment(this)
        val controller = controller(provider, env)

        controller.acquire()
        advanceTimeBy(6_000L)
        runCurrent()
        val callsBeforeBackground = provider.calls.size

        env.isForeground.value = false
        advanceTimeBy(600_000L)
        runCurrent()

        assertTrue(controller.state.value is AdLoadState.Paused)
        assertEquals(callsBeforeBackground, provider.calls.size)

        provider.results.addFirst(AdResult.Success(creative))
        env.isForeground.value = true
        advanceUntilIdle()

        assertTrue(controller.state.value is AdLoadState.Ready)
        assertTrue(provider.calls.size > callsBeforeBackground)
    }

    @Test
    fun `caso H volver a la pantalla reutiliza el anuncio vigente`() = runTest {
        val provider = FakeProvider(results = ArrayDeque(listOf(AdResult.Success(creative))))
        val env = TestEnvironment(this)
        val controller = controller(provider, env)

        controller.acquire()
        advanceUntilIdle()
        controller.release()
        advanceTimeBy(30_000L)

        controller.acquire()
        advanceUntilIdle()

        assertTrue(controller.state.value is AdLoadState.Ready)
        assertEquals(1, provider.calls.size)
    }

    @Test
    fun `caso I sin inventario no hay bucle agresivo`() = runTest {
        val provider = FakeProvider(
            results = ArrayDeque(
                listOf(
                    AdResult.NoFill,
                    AdResult.Success(creative),
                ),
            ),
        )
        val env = TestEnvironment(this)
        val controller = controller(provider, env)

        controller.acquire()
        advanceTimeBy(10_000L)
        runCurrent()

        assertEquals(1, provider.calls.size)
        assertTrue(controller.state.value is AdLoadState.WaitingRetry)

        advanceTimeBy(115_000L)
        runCurrent()
        assertEquals(2, provider.calls.size)
        assertTrue(controller.state.value is AdLoadState.Ready)
    }

    @Test
    fun `caso J un error de autorizacion no genera reintentos`() = runTest {
        val provider = FakeProvider(
            results = ArrayDeque(listOf(AdResult.Success(creative))),
            authorization = AdAuthorization.PendingWrittenConsent("pendiente"),
        )
        val env = TestEnvironment(this)
        val controller = controller(provider, env)

        controller.acquire()
        advanceTimeBy(3_600_000L)
        runCurrent()

        assertTrue(controller.state.value is AdLoadState.Unauthorized)
        assertEquals(0, provider.calls.size)
    }

    @Test
    fun `un error de configuracion termina sin reintentos`() = runTest {
        val provider = FakeProvider(
            results = ArrayDeque(
                listOf(
                    AdResult.Failure(AdFailure(AdFailureKind.CONFIGURATION, "unidad inválida")),
                    AdResult.Success(creative),
                ),
            ),
        )
        val env = TestEnvironment(this)
        val controller = controller(provider, env)

        controller.acquire()
        advanceUntilIdle()

        assertTrue(controller.state.value is AdLoadState.Disabled)
        assertEquals(1, provider.calls.size)
    }

    @Test
    fun `un fallo de representacion reabre el ciclo de recuperacion`() = runTest {
        val provider = FakeProvider(
            results = ArrayDeque(
                listOf(
                    AdResult.Success(creative),
                    AdResult.Success(creative),
                ),
            ),
        )
        val env = TestEnvironment(this)
        val controller = controller(provider, env)

        controller.acquire()
        advanceUntilIdle()
        assertTrue(controller.state.value is AdLoadState.Ready)

        controller.onCreativeFailure(AdFailure(AdFailureKind.PROVIDER, "render"))
        advanceUntilIdle()

        assertTrue(controller.state.value is AdLoadState.Ready)
        assertEquals(2, provider.calls.size)
    }

    private fun TestScope.controller(
        provider: FakeProvider,
        environment: TestEnvironment,
    ): AdController {
        provider.clock = { testScheduler.currentTime }
        return AdController(
            placement = AdPlacement.MAIN_BANNER,
            provider = provider,
            environment = environment,
            retryPolicy = AdRetryPolicy(
                retryDelaysMs = longArrayOf(5_000, 15_000, 30_000, 60_000, 120_000),
                noFillDelaysMs = longArrayOf(120_000, 300_000, 600_000),
                jitterRatio = 0.0,
            ),
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()),
            timeoutMs = 1_000L,
        )
    }

    private class TestEnvironment(
        private val scope: CoroutineScope,
        online: Boolean = true,
        foreground: Boolean = true,
    ) : AdEnvironment {
        private val scheduler = scope as TestScope
        override val isOnline = MutableStateFlow(online)
        override val isForeground = MutableStateFlow(foreground)

        override fun nowMillis(): Long = scheduler.testScheduler.currentTime
    }

    /**
     * Proveedor simulado. Registra el instante de cada solicitud y la
     * concurrencia máxima para detectar duplicados.
     */
    private class FakeProvider(
        val results: ArrayDeque<AdResult>,
        private val authorization: AdAuthorization = AdAuthorization.Authorized("prueba"),
        private val latencyMs: Long = 10L,
        private val hangFirst: Boolean = false,
    ) : AdProvider {
        override val id: String = "fake"
        val calls = mutableListOf<Long>()
        var maxConcurrent = 0
        var clock: () -> Long = { 0L }
        private var concurrent = 0
        private var first = true

        override fun authorization(placement: AdPlacement): AdAuthorization = authorization

        override suspend fun load(request: AdRequest): AdResult {
            calls += clock()
            concurrent += 1
            maxConcurrent = maxOf(maxConcurrent, concurrent)
            try {
                if (hangFirst && first) {
                    first = false
                    delay(60_000L)
                } else {
                    delay(latencyMs)
                }
                return results.removeFirstOrNull() ?: AdResult.NoFill
            } finally {
                concurrent -= 1
            }
        }
    }
}
