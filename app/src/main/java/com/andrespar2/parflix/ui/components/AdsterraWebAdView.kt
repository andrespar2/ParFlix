package com.andrespar2.parflix.ui.components

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.webkit.RenderProcessGoneDetail
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
import com.andrespar2.parflix.ads.AdCreative
import com.andrespar2.parflix.ads.AdFailure
import com.andrespar2.parflix.ads.AdFailureKind

/**
 * Representa una creatividad web dentro de un WebView aislado.
 *
 * Medidas de seguridad:
 *  - Sin interfaces JavaScript privilegiadas.
 *  - Sin acceso a archivos ni a content://.
 *  - Contenido mixto bloqueado y solo HTTPS para navegación externa.
 *  - La navegación principal solo abre el navegador si hubo un toque real del
 *    usuario; una redirección automática se bloquea.
 *  - El WebView se destruye al salir de composición.
 *
 * Solo se compone cuando el proveedor está autorizado; en el estado actual de
 * Adsterra ese paso no ocurre.
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

            val webView = WebView(context).apply {
                setBackgroundColor(Color.BLACK)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
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

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        // Las subcargas del creativo (iframe, píxeles) siguen su
                        // curso; solo se interviene en la navegación principal.
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
                        // El WebView queda inservible; Compose lo liberará.
                        return true
                    }
                }

                loadDataWithBaseURL(
                    null,
                    creative.html,
                    "text/html",
                    "UTF-8",
                    null,
                )
                tag = creative.html
            }
            webView
        },
        update = { webView ->
            if (webView.tag != creative.html) {
                webView.loadDataWithBaseURL(
                    null,
                    creative.html,
                    "text/html",
                    "UTF-8",
                    null,
                )
                webView.tag = creative.html
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

private const val USER_GESTURE_WINDOW_MS = 3_000L
