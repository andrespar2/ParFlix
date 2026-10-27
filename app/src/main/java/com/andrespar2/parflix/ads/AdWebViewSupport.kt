package com.andrespar2.parflix.ads

import android.webkit.WebView

/**
 * Las unidades web de Adsterra dependen del motor WebView. En versiones muy
 * antiguas el renderizador puede no pintar la creatividad aunque el HTML exista.
 * Para no dejar tarjetas vacías ni provocar cierres del proceso de renderizado,
 * la publicidad se muestra solo cuando el WebView es suficientemente moderno.
 */
object AdWebViewSupport {

    private const val MIN_WEBVIEW_MAJOR = 100

    fun isSupported(): Boolean {
        val packageInfo = WebView.getCurrentWebViewPackage() ?: return false
        val major = packageInfo.versionName
            ?.substringBefore('.')
            ?.toIntOrNull()
            ?: return false
        return major >= MIN_WEBVIEW_MAJOR
    }
}
