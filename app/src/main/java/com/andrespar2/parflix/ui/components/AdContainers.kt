package com.andrespar2.parflix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andrespar2.parflix.ads.AdConfiguration
import com.andrespar2.parflix.ads.AdController
import com.andrespar2.parflix.ads.AdCreative
import com.andrespar2.parflix.ads.AdWebViewSupport

/**
 * Contenedor del banner 320x50.
 *
 * - Respeta el tamaño original del creativo y lo centra.
 * - Si el ancho disponible es menor que 320 dp, no se muestra: no se deforma
 *   ni se recorta el anuncio.
 * - Sin inventario, error o WebView demasiado antigua no ocupa espacio.
 * - El ciclo de vida del controlador se cancela al salir de composición.
 */
@Composable
fun BannerAdContainer(
    controller: AdController,
    modifier: Modifier = Modifier,
) {
    // El banner de Adsterra no se representa de forma fiable en WebView muy
    // antiguas. En esos equipos no se reserva un hueco vacío.
    if (!AdWebViewSupport.isSupported()) return

    val state by controller.state.collectAsStateWithLifecycle()

    DisposableEffect(controller) {
        controller.acquire()
        onDispose { controller.release() }
    }

    val creative = state.creative as? AdCreative.WebTag ?: return

    LaunchedEffect(creative) { controller.markVisible() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        if (maxWidth < BANNER_WIDTH) return@BoxWithConstraints

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(bottom = 4.dp),
        ) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            )
            Text(
                text = AdConfiguration.AD_LABEL,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
            )
            AdsterraWebAdView(
                creative = creative,
                onFailure = controller::onCreativeFailure,
                modifier = Modifier
                    .clipToBounds()
                    .width(BANNER_WIDTH)
                    .height(BANNER_HEIGHT),
            )
        }
    }
}

/**
 * Contenedor de anuncio nativo.
 *
 * Mantiene el lenguaje visual de las tarjetas (fondo gris oscuro, esquinas
 * redondeadas) y una etiqueta "Patrocinado" siempre visible. No imita una
 * película ni un botón de reproducción.
 */
@Composable
fun NativeAdContainer(
    controller: AdController,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsStateWithLifecycle()

    DisposableEffect(controller) {
        controller.acquire()
        onDispose { controller.release() }
    }

    val creative = state.creative as? AdCreative.WebTag ?: return

    LaunchedEffect(creative) { controller.markVisible() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
    ) {
        Text(
            text = AdConfiguration.SPONSORED_LABEL.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            AdsterraWebAdView(
                creative = creative,
                onFailure = controller::onCreativeFailure,
                modifier = Modifier
                    .clipToBounds()
                    .fillMaxWidth()
                    .height(NATIVE_HEIGHT),
            )
        }
    }
}

private val BANNER_WIDTH = 320.dp
private val BANNER_HEIGHT = 50.dp
private val NATIVE_HEIGHT = 300.dp
