package com.andrespar2.parflix.ads

/**
 * Reglas de frecuencia y distribución de los anuncios nativos.
 *
 * Son funciones puras: no dependen de Compose ni del SDK, de modo que se
 * pueden probar con tests unitarios y ajustar sin tocar la interfaz.
 *
 * Las posiciones son índices sobre la lista de contenido real: un anuncio en
 * la posición `p` se muestra justo antes del elemento `p`.
 */
object AdsDistribution {

    /**
     * Posiciones donde insertar anuncios nativos para un catálogo de
     * [itemCount] elementos reales.
     *
     * - Primer anuncio tras [AdsConfig.NATIVE_FIRST_AFTER_CARDS] tarjetas.
     * - Después, como mínimo uno cada [AdsConfig.NATIVE_MIN_GAP_BETWEEN_ADS].
     * - Nunca más de [AdsConfig.NATIVE_MAX_PER_SCREEN] por pantalla.
     * - Jamás en la última posición (el anuncio siempre tiene contenido detrás).
     */
    fun nativeAdPositions(
        itemCount: Int,
        maxAds: Int = AdsConfig.NATIVE_MAX_PER_SCREEN,
    ): List<Int> {
        if (maxAds <= 0 || itemCount <= AdsConfig.NATIVE_FIRST_AFTER_CARDS) return emptyList()

        val positions = mutableListOf<Int>()
        var next = AdsConfig.NATIVE_FIRST_AFTER_CARDS
        while (positions.size < maxAds && next < itemCount) {
            positions.add(next)
            next += AdsConfig.NATIVE_MIN_GAP_BETWEEN_ADS
        }
        return positions
    }

    /**
     * Cuántos anuncios nativos se pueden colocar como máximo en una pantalla
     * con [itemCount] elementos reales. Se usa para no pedir más inventario
     * del necesario.
     */
    fun maxAdsFor(itemCount: Int): Int = nativeAdPositions(itemCount).size
}
