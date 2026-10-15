package com.andrespar2.parflix.ui.screens.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andrespar2.parflix.domain.MediaType
import com.andrespar2.parflix.ui.components.ErrorState
import com.andrespar2.parflix.ui.rememberContainer

private const val VIMEUS_HOST = "vimeus.com"

/**
 * Reproductor integrado (WebView) sobre la página de inserción oficial de
 * Vimeus.
 *
 * Limitaciones documentadas:
 * - No se intenta saltar ninguna protección del proveedor ni falsificar origen
 *   o `Referer`; se carga la URL tal cual la ofrece Vimeus.
 * - El proveedor no expone una API pública de posición de reproducción, así que
 *   "Continuar viendo" solo guarda el último episodio abierto, nunca un minuto
 *   de avance estimado.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PlayerScreen(
    mediaType: MediaType,
    tmdbId: Int,
    season: Int,
    episode: Int,
    onBack: () -> Unit,
) {
    val container = rememberContainer()
    val viewModel: PlayerViewModel = viewModel(
        key = "player-${mediaType.name}-$tmdbId-$season-$episode",
        factory = PlayerViewModel.factory(container, mediaType, tmdbId, season, episode),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // La reproducción se hace en horizontal; se restaura la orientación previa al salir.
    DisposableEffect(activity) {
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    var customView by remember { mutableStateOf<View?>(null) }
    var customCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    val fullscreenHost = remember { mutableStateOf<FrameLayout?>(null) }
    var progress by remember { mutableIntStateOf(100) }
    var webError by remember { mutableStateOf<String?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val isFullscreen = customView != null

    // Modo inmersivo solo durante la reproducción.
    DisposableEffect(activity, isFullscreen) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (window != null) {
            if (isFullscreen) {
                controller?.hide(WindowInsetsCompat.Type.systemBars())
                controller?.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller?.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { }
    }

    BackHandler(enabled = true) {
        when {
            customView != null -> {
                fullscreenHost.value?.removeView(customView)
                customView = null
                customCallback?.onCustomViewHidden()
                customCallback = null
            }
            webViewRef?.canGoBack() == true -> webViewRef?.goBack()
            else -> onBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            customView?.let { fullscreenHost.value?.removeView(it) }
            webViewRef?.apply {
                loadUrl("about:blank")
                destroy()
            }
            webViewRef = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.error != null -> ErrorState(
                message = state.error!!,
                onRetry = { viewModel.load() },
            )

            state.url == null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            else -> {
                val url = state.url!!
                Column(Modifier.fillMaxSize()) {
                    if (!isFullscreen) {
                        PlayerBar(title = state.title, onBack = onBack)
                        if (progress < 100) {
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Box(Modifier.fillMaxSize()) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    configureForPlayback()
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            view: WebView,
                                            request: WebResourceRequest,
                                        ): Boolean {
                                            val target = request.url
                                            val allowed = target.scheme == "https" &&
                                                isAllowedHost(target.host)
                                            if (allowed) return false
                                            // Enlaces externos o no autorizados: fuera de la app.
                                            runCatching {
                                                view.context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, target)
                                                )
                                            }
                                            return true
                                        }

                                        override fun onReceivedError(
                                            view: WebView,
                                            request: WebResourceRequest,
                                            error: WebResourceError,
                                        ) {
                                            if (request.isForMainFrame) {
                                                webError = error.description?.toString()
                                                    ?: "No se pudo cargar el reproductor"
                                            }
                                        }
                                    }
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(
                                            view: WebView,
                                            newProgress: Int,
                                        ) {
                                            progress = newProgress
                                        }

                                        override fun onShowCustomView(
                                            view: View,
                                            callback: CustomViewCallback,
                                        ) {
                                            if (customView != null) {
                                                callback.onCustomViewHidden()
                                                return
                                            }
                                            customView = view
                                            customCallback = callback
                                            fullscreenHost.value?.addView(
                                                view,
                                                FrameLayout.LayoutParams(
                                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                                ),
                                            )
                                        }

                                        override fun onHideCustomView() {
                                            customView?.let { fullscreenHost.value?.removeView(it) }
                                            customView = null
                                            customCallback?.onCustomViewHidden()
                                            customCallback = null
                                        }
                                    }
                                    webViewRef = this
                                }
                            },
                        )

                        if (webError != null) {
                            ErrorState(
                                message = webError!!,
                                onRetry = {
                                    webError = null
                                    webViewRef?.reload()
                                },
                            )
                        }
                    }
                }
            }
        }

        // Contenedor del vídeo a pantalla completa del reproductor.
        AndroidView(
            modifier = if (isFullscreen) Modifier.fillMaxSize() else Modifier.size(0.dp),
            factory = { ctx ->
                FrameLayout(ctx).also { fullscreenHost.value = it }
            },
        )
    }

    // Carga inicial y recargas.
    LaunchedEffect(state.url) {
        val url = state.url
        if (url != null) {
            webError = null
            progress = 0
            webViewRef?.loadUrl(url)
        }
    }
}

@Composable
private fun PlayerBar(title: String?, onBack: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
        }
        Text(
            text = title.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            maxLines = 1,
        )
    }
}

private fun WebView.configureForPlayback() {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.mediaPlaybackRequiresUserGesture = false
    settings.loadWithOverviewMode = true
    settings.useWideViewPort = true
    settings.cacheMode = WebSettings.LOAD_DEFAULT
    settings.setSupportMultipleWindows(false)
    settings.javaScriptCanOpenWindowsAutomatically = false
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    setBackgroundColor(android.graphics.Color.BLACK)
}

/** Solo se permite HTTPS del dominio autorizado en la ventana principal. */
private fun isAllowedHost(host: String?): Boolean {
    val value = host?.lowercase() ?: return false
    return value == VIMEUS_HOST || value.endsWith(".$VIMEUS_HOST")
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
