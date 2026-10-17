package com.andrespar2.parflix.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reglas de frecuencia de los anuncios nativos.
 *
 * Se prueban como funciones puras: no dependen del SDK ni de Compose, así que
 * cualquier ajuste de frecuencia queda cubierto por estos casos.
 */
class AdsDistributionTest {

    @Test
    fun `sin contenido suficiente no coloca anuncios`() {
        assertTrue(AdsDistribution.nativeAdPositions(0).isEmpty())
        assertTrue(AdsDistribution.nativeAdPositions(1).isEmpty())
        assertTrue(
            AdsDistribution.nativeAdPositions(AdsConfig.NATIVE_FIRST_AFTER_CARDS).isEmpty(),
        )
    }

    @Test
    fun `el primer anuncio va tras las tarjetas configuradas`() {
        assertEquals(
            listOf(AdsConfig.NATIVE_FIRST_AFTER_CARDS),
            AdsDistribution.nativeAdPositions(AdsConfig.NATIVE_FIRST_AFTER_CARDS + 1),
        )
    }

    @Test
    fun `respeta el hueco minimo entre anuncios`() {
        val positions = AdsDistribution.nativeAdPositions(itemCount = 200)

        assertEquals(2, positions.size)
        assertEquals(
            AdsConfig.NATIVE_MIN_GAP_BETWEEN_ADS,
            positions[1] - positions[0],
        )
    }

    @Test
    fun `nunca supera el maximo por pantalla`() {
        val positions = AdsDistribution.nativeAdPositions(itemCount = 10_000)
        assertEquals(AdsConfig.NATIVE_MAX_PER_SCREEN, positions.size)
    }

    @Test
    fun `ningun anuncio queda en la ultima posicion`() {
        for (count in 1..300) {
            AdsDistribution.nativeAdPositions(count).forEach { position ->
                assertTrue(
                    "anuncio en $position para un catalogo de $count elementos",
                    position < count,
                )
            }
        }
    }

    @Test
    fun `nunca coloca dos anuncios consecutivos`() {
        AdsDistribution.nativeAdPositions(itemCount = 500).zipWithNext().forEach { (first, second) ->
            assertTrue(second - first > 1)
        }
    }

    @Test
    fun `el numero maximo de anuncios coincide con las posiciones`() {
        for (count in 0..300) {
            assertEquals(
                AdsDistribution.nativeAdPositions(count).size,
                AdsDistribution.maxAdsFor(count),
            )
        }
    }

    @Test
    fun `un maximo de cero desactiva la publicidad nativa`() {
        assertTrue(AdsDistribution.nativeAdPositions(itemCount = 100, maxAds = 0).isEmpty())
    }
}
