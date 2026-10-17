package com.andrespar2.parflix.ads

/**
 * Configuración de la publicidad de ParFlix (Start.io).
 *
 * Es el único punto donde se ajustan identificadores, formatos y frecuencia.
 * Cambiar la frecuencia no debe obligar a tocar pantallas ni componentes.
 */
object AdsConfig {

    /**
     * App ID real de ParFlix en Start.io. Nunca usar el ID de demostración
     * (`205489527`) fuera de builds de prueba.
     */
    const val START_IO_APP_ID = "209689415"

    /**
     * Los anuncios de prueba solo se activan en builds de depuración.
     * En release se solicitan anuncios reales, sujetos a inventario.
     */
    const val TEST_ADS_IN_DEBUG = true

    /**
     * Los "return ads" (anuncios al salir de la app) están desactivados:
     * ParFlix no interrumpe al usuario con formatos automáticos.
     */
    const val RETURN_ADS_ENABLED = false

    /** Primer anuncio nativo tras este número de tarjetas reales. */
    const val NATIVE_FIRST_AFTER_CARDS = 8

    /** Número mínimo de tarjetas reales entre dos anuncios nativos. */
    const val NATIVE_MIN_GAP_BETWEEN_ADS = 15

    /** Máximo de anuncios nativos por pantalla o sesión de desplazamiento. */
    const val NATIVE_MAX_PER_SCREEN = 2

    /**
     * Latencia mínima (ms) entre intentos de cargar anuncios cuando la
     * anterior petición falló, para no repetir peticiones en bucle.
     */
    const val AD_RETRY_COOLDOWN_MS = 60_000L

    /** Etiqueta visible que acompaña a toda publicidad. */
    const val AD_LABEL = "Anuncio"
    const val SPONSORED_LABEL = "Patrocinado"
}
