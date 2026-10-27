package com.andrespar2.parflix.ads

/**
 * Configuración central de publicidad de ParFlix.
 *
 * Todo lo que puede cambiar (identificadores, formatos habilitados, tiempos de
 * reintento y etiquetas visibles) vive aquí. Las pantallas y los contenedores
 * no conocen claves del proveedor ni decisiones de autorización.
 */
object AdConfiguration {

    // ---------------------------------------------------------------------
    // Adsterra: cuenta y unidades
    // ---------------------------------------------------------------------

    const val PUBLISHER_NAME = "Pardinho"
    const val WEBSITE = "andrespar2.github.io"
    const val WEBSITE_ID = "6155300"

    /** Identificador administrativo de la unidad, no la clave del script. */
    const val NATIVE_BANNER_ID = "31953766"

    /** Identificador administrativo de la unidad, no la clave del script. */
    const val BANNER_320X50_ID = "31953767"

    /** Claves reales de los scripts entregados por GET CODE. */
    const val BANNER_KEY = "52808af04c50170b7f58d45dc3674f2c"
    const val NATIVE_KEY = "0b49e206ab92aabeebe7d6c0c2ac4ebd"

    const val BANNER_SCRIPT_URL =
        "https://bancadeltempoidea.org/22/52808af04c50170b7f58d45dc3674f2c"
    const val NATIVE_SCRIPT_URL =
        "https://bancadeltempoidea.org/21/0b49e206ab92aabeebe7d6c0c2ac4ebd"

    /**
     * Las unidades facilitadas son etiquetas web. Antes de cargarlas dentro de
     * una WebView de la APK hace falta autorización escrita de Adsterra:
     * sus términos para publishers limitan la etiqueta a páginas del sitio
     * aprobado (andrespar2.github.io) y exigen consentimiento previo para
     * iframes. Mientras no exista esa autorización, la app no realiza ninguna
     * petición publicitaria.
     *
     * Cambiar estos valores solo debe hacerse con la autorización documentada.
     */
    const val ANDROID_WEBVIEW_AUTHORIZED = false

    /** Interruptores independientes por formato, además de la autorización. */
    const val ANDROID_BANNER_ENABLED = false
    const val ANDROID_NATIVE_ENABLED = false

    val androidAuthorization: AdAuthorization
        get() = if (ANDROID_WEBVIEW_AUTHORIZED) {
            AdAuthorization.Authorized("WebView autorizada para unidades Adsterra")
        } else {
            AdAuthorization.PendingWrittenConsent(
                "Las unidades Adsterra son etiquetas web para el sitio aprobado. " +
                    "Falta autorización escrita para usarlas en una WebView de la APK."
            )
        }

    fun authorizationFor(format: AdFormat): AdAuthorization {
        val global = androidAuthorization
        if (global !is AdAuthorization.Authorized) return global

        return when (format) {
            AdFormat.BANNER_320X50 -> if (ANDROID_BANNER_ENABLED) {
                global
            } else {
                AdAuthorization.Disabled("Banner de Android deshabilitado por configuración")
            }

            AdFormat.NATIVE -> if (ANDROID_NATIVE_ENABLED) {
                global
            } else {
                AdAuthorization.Disabled("Anuncio nativo de Android deshabilitado por configuración")
            }
        }
    }

    // ---------------------------------------------------------------------
    // Frecuencia de anuncios nativos
    // ---------------------------------------------------------------------

    /** Primer anuncio nativo tras este número de tarjetas reales. */
    const val NATIVE_FIRST_AFTER_CARDS = 8

    /** Número mínimo de tarjetas reales entre dos anuncios nativos. */
    const val NATIVE_MIN_GAP_BETWEEN_ADS = 15

    /** Máximo de anuncios nativos por pantalla o sesión de desplazamiento. */
    const val NATIVE_MAX_PER_SCREEN = 2

    // ---------------------------------------------------------------------
    // Reintentos (errores recuperables)
    // ---------------------------------------------------------------------

    /** Espera inicial para errores transitorios: 5, 15, 30, 60 y 120 s. */
    val RETRY_DELAYS_MS = longArrayOf(5_000, 15_000, 30_000, 60_000, 120_000)

    /** Techo de la espera: cinco minutos. */
    const val RETRY_MAX_DELAY_MS = 300_000L

    /**
     * Sin inventario no se reintenta rápido: el proveedor necesita margen y
     * repetir solicitudes no crea anuncios.
     */
    val NO_FILL_DELAYS_MS = longArrayOf(120_000, 300_000, 600_000)

    const val NO_FILL_MAX_DELAY_MS = 600_000L

    /** Margen aleatorio para evitar picos sincronizados entre placements. */
    const val RETRY_JITTER_RATIO = 0.2

    /** Tiempo máximo que se espera una carga antes de liberar la solicitud. */
    const val LOAD_TIMEOUT_MS = 12_000L

    /** Anuncio ya mostrado reutilizable al volver a una pestaña. */
    const val CREATIVE_FRESHNESS_MS = 10 * 60_000L

    // ---------------------------------------------------------------------
    // Etiquetas visibles
    // ---------------------------------------------------------------------

    const val AD_LABEL = "Publicidad"
    const val SPONSORED_LABEL = "Patrocinado"
}
