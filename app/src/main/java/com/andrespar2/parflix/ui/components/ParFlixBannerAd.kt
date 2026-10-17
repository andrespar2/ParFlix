package com.andrespar2.parflix.ui.components

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andrespar2.parflix.ads.AdSlotState
import com.andrespar2.parflix.ads.AdsManager

/** Alto oficial del banner de Start.io (320x50 dp). */
private val BannerHeight = 50.dp
private val BannerMaxWidth = 320.dp

/**
 * Banner publicitario compacto de ParFlix.
 *
 * - 320x50 dp, centrado horizontalmente.
 * - Fondo AMOLED negro con separador discreto y etiqueta "Publicidad".
 * - Si el SDK no entrega inventario, no compone nada: no deja huecos.
 * - La vista del anuncio se reutiliza (cacheada en [AdsManager]), de modo que
 *   cambiar de pestaña o recomponer no genera peticiones ni banners duplicados.
 *
 * No debe colocarse en el reproductor, detalles, ajustes ni diálogos.
 */
@Composable
fun ParFlixBannerAd(
    adsManager: AdsManager,
    modifier: Modifier = Modifier,
) {
    val isInitialized by adsManager.isInitialized.collectAsStateWithLifecycle()
    val bannerState by adsManager.bannerState.collectAsStateWithLifecycle()

    LaunchedEffect(isInitialized, bannerState) {
        if (isInitialized) adsManager.requestBannerIfNeeded(force = false)
    }

    val bannerView = (bannerState as? AdSlotState.Ready)?.ad as? View ?: return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        )

        Text(
            text = "Publicidad",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            val width = minOf(BannerMaxWidth, maxWidth)
            AndroidView(
                modifier = Modifier
                    .width(width)
                    .height(BannerHeight),
                factory = {
                    // Al volver a una pestaña la vista puede seguir vinculada a
                    // su contenedor anterior; se desvincula antes de reutilizarla.
                    (bannerView.parent as? ViewGroup)?.removeView(bannerView)
                    bannerView
                },
            )
        }
    }
}
