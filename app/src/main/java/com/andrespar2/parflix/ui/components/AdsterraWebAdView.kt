package com.andrespar2.parflix.ui.components

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.webkit.CookieManager
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.andrespar2.parflix.BuildConfig
import com.andrespar2.parflix.ads.AdCreative
import com.andrespar2.parflix.ads.AdFailure
import com.andrespar2.parflix.ads.AdFailureKind
import com.andrespar2.parflix.ads.AdFormat

/**
 * Representa una unidad web de Adsterra dentro de un WebView aislado.
 *
 * La unidad se carga desde una página real del sitio aprobado, nunca inyectando
 * la etiqueta en un documento local con un origen artificial.
 *
 * Medidas de seguridad:
 *  - Sin interfaces JavaScript privilegiadas.
 *  - Sin acceso a archivos ni a content://.
 *  - Contenido mixto bloqueado.
 *  - La navegación principal solo abre el navegador si hubo un toque real.
 *  - El WebView se destruye al salir de composición.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AdsterraWebAdView(
    creative: AdCreative.WebTag,
    onFailure: (AdFailure) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    AndroidView(
        modifier = modifier,
        factory = {
            var lastUserTouchAt = 0L

            CookieManager.getInstance().setAcceptCookie(true)

            val webView = WebView(context).apply {
                setBackgroundColor(Color.BLACK)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                    mediaPlaybackRequiresUserGesture = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    setGeolocationEnabled(false)
                }

                setOnTouchListener { _, event ->
                    if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                        lastUserTouchAt = SystemClock.elapsedRealtime()
                    }
                    false
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        if (BuildConfig.DEBUG) {
                            Log.d(
                                TAG,
                                "WebView console: ${message.message()} " +
                                    "(${message.sourceId()}:${message.lineNumber()})",
                            )
                        }
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(
                        view: WebView,
                        url: String?,
                        favicon: Bitmap?,
                    ) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "Página publicitaria: $url")
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "Unidad cargada: format=${creative.format}")
                        scheduleContentCheck(view, creative.format, onFailure)
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        // Las subcargas del creativo siguen su curso; solo se
                        // interviene en la navegación principal.
                        if (!request.isForMainFrame) return false

                        val url = request.url?.toString().orEmpty()
                        val recentTouch =
                            SystemClock.elapsedRealtime() - lastUserTouchAt <= USER_GESTURE_WINDOW_MS
                        val isWeb = url.startsWith("https://") || url.startsWith("http://")

                        // Sin toque del usuario no se abre nada: evita
                        // redirecciones automáticas y pestañas inesperadas.
                        if (isWeb && recentTouch) {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }.onFailure { error ->
                                if (error !is ActivityNotFoundException) {
                                    onFailure(
                                        AdFailure(
                                            AdFailureKind.PROVIDER,
                                            "No se pudo abrir el destino del anuncio",
                                        ),
                                    )
                                }
                            }
                        }
                        return true
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        if (!request.isForMainFrame) return
                        onFailure(
                            AdFailure(
                                AdFailureKind.NETWORK,
                                "Error al cargar la creatividad (${error.errorCode})",
                            ),
                        )
                    }

                    override fun onReceivedHttpError(
                        view: WebView,
                        request: WebResourceRequest,
                        errorResponse: WebResourceResponse,
                    ) {
                        if (!request.isForMainFrame) return
                        val retryAfterMs = errorResponse.responseHeaders
                            ?.get("Retry-After")
                            ?.toLongOrNull()
                            ?.times(1_000L)
                        onFailure(AdFailure.fromHttp(errorResponse.statusCode, retryAfterMs))
                    }

                    override fun onRenderProcessGone(
                        view: WebView,
                        detail: RenderProcessGoneDetail,
                    ): Boolean {
                        onFailure(
                            AdFailure(
                                AdFailureKind.PROVIDER,
                                "El proceso de renderizado del anuncio se detuvo",
                            ),
                        )
                        return true
                    }
                }

                loadCreative(creative)
            }
            webView
        },
        update = { webView ->
            if (webView.tag != creative.key()) {
                webView.loadCreative(creative)
            }
        },
        onRelease = { webView ->
            runCatching { webView.stopLoading() }
            runCatching { webView.loadUrl("about:blank") }
            runCatching { webView.clearHistory() }
            runCatching { webView.removeAllViews() }
            runCatching { webView.destroy() }
        },
    )
}

private fun AdCreative.WebTag.key(): String = url ?: html.orEmpty()

private fun WebView.loadCreative(creative: AdCreative.WebTag) {
    val pageUrl = creative.url
    if (!pageUrl.isNullOrBlank()) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Cargando página publicitaria: $pageUrl")
        loadUrl(pageUrl)
        tag = creative.key()
        return
    }

    loadDataWithBaseURL(
        null,
        creative.html.orEmpty(),
        "text/html",
        "UTF-8",
        null,
    )
    tag = creative.key()
}

/**
 * El proveedor puede devolver una página sin creatividad (no fill). Si tras
 * unos segundos no hay un iframe, imagen o texto visible, se oculta el
 * contenedor y el controlador aplica su espera de no-fill. No se recarga nada
 * desde aquí.
 */
private fun scheduleContentCheck(
    view: WebView,
    format: AdFormat,
    onFailure: (AdFailure) -> Unit,
) {
    val delay = if (format == AdFormat.NATIVE) {
        NATIVE_CONTENT_CHECK_DELAY_MS
    } else {
        BANNER_CONTENT_CHECK_DELAY_MS
    }
    view.postDelayed(
        {
            if (!view.isAttachedToWindow) return@postDelayed
            view.evaluateJavascript(CONTENT_CHECK_JS) { result ->
                if (result?.trim('"') != "true") {
                    onFailure(
                        AdFailure(
                            AdFailureKind.NO_FILL,
                            "La unidad no devolvió contenido visible",
                        ),
                    )
                }
            }
        },
        delay,
    )
}

private const val USER_GESTURE_WINDOW_MS = 3_000L
private const val BANNER_CONTENT_CHECK_DELAY_MS = 8_000L
private const val NATIVE_CONTENT_CHECK_DELAY_MS = 15_000L
private const val TAG = "ParFlixAds"

private const val CONTENT_CHECK_JS = """
(function () {
  var nodes = document.querySelectorAll('iframe,img,ins,canvas,video');
  for (var i = 0; i < nodes.length; i++) {
    var rect = nodes[i].getBoundingClientRect();
    if (rect.width > 4 && rect.height > 4) return true;
  }
  var text = (document.body.innerText || '').trim();
  return text.length > 2;
})()
"""
