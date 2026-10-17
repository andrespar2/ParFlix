package com.andrespar2.parflix.ui.components

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.Image
import com.andrespar2.parflix.ads.AdsConfig
import com.startapp.sdk.ads.nativead.NativeAdDetails

/**
 * Tarjeta de anuncio nativo de ParFlix.
 *
 * Reutiliza el lenguaje visual de las tarjetas del catálogo (fondo AMOLED,
 * superficies gris oscuro, esquinas redondeadas) pero se distingue claramente
 * del contenido editorial mediante la etiqueta "Patrocinado".
 *
 * - Solo muestra datos reales del anunciante (imagen, título, descripción y
 *   botón de acción). No inventa portadas, notas ni sinopsis.
 * - El clic lo gestiona el SDK mediante `registerViewForInteraction()`, que
 *   exige medir el contenedor con `IntrinsicSize.Max` para ser clicable.
 * - Al salir de composición se llama a `unregisterView()` para no filtrar
 *   referencias.
 */
@Composable
fun ParFlixNativeAdCard(
    ad: NativeAdDetails,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            // Obligatorio en Compose: sin altura intrínseca el SDK no puede
            // registrar la interacción del anuncio.
            .height(IntrinsicSize.Max)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AdImage(ad)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (ad.imageBitmap != null) 12.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SponsoredLabel()

                val title = ad.title.orEmpty()
                if (title.isNotBlank()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                val description = ad.description.orEmpty()
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                val callToAction = ad.callToAction.orEmpty()
                if (callToAction.isNotBlank()) {
                    CallToActionButton(callToAction)
                }
            }
        }

        // Vista transparente que registra la interacción con el SDK. El clic
        // abre el destino real del anunciante; no hay redirecciones propias.
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val result = View(context)
                ad.registerViewForInteraction(result)
                result
            },
        )
    }

    DisposableEffect(ad) {
        onDispose { ad.unregisterView() }
    }
}

@Composable
private fun AdImage(ad: NativeAdDetails) {
    val bitmap = ad.imageBitmap ?: return
    Box(
        modifier = Modifier
            .size(width = 96.dp, height = 120.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Imagen del anuncio",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun SponsoredLabel() {
    Text(
        text = AdsConfig.SPONSORED_LABEL.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * Botón de acción del anunciante. El clic real lo captura la vista registrada
 * del SDK, por eso aquí no se programa ninguna navegación propia.
 */
@Composable
private fun CallToActionButton(label: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(14.dp),
        )
    }
}
