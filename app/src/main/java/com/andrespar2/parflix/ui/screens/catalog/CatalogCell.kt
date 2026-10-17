package com.andrespar2.parflix.ui.screens.catalog

import com.andrespar2.parflix.ads.AdsDistribution
import com.andrespar2.parflix.data.local.entity.CatalogItemEntity
import com.startapp.sdk.ads.nativead.NativeAdDetails

/**
 * Elemento de la cuadrícula del catálogo: contenido real o anuncio nativo.
 *
 * Mantener las claves estables de las tarjetas reales evita que Compose
 * recree elementos al insertar publicidad. Los anuncios usan su propia clave,
 * que nunca puede chocar con la de un título.
 */
sealed interface CatalogCell {
    val key: String

    data class Content(
        val item: CatalogItemEntity,
    ) : CatalogCell {
        override val key: String = "${item.mediaType}:${item.tmdbId}"
    }

    data class Sponsored(
        val ad: NativeAdDetails,
        val position: Int,
    ) : CatalogCell {
        override val key: String = "sponsored:$position"
    }
}

/**
 * Intercala los anuncios nativos disponibles en la lista real según las
 * reglas de [AdsDistribution]. Si hay menos anuncios que ranuras, simplemente
 * se muestran los que haya; nunca se reordena ni se altera el contenido.
 */
fun buildCatalogCells(
    items: List<CatalogItemEntity>,
    ads: List<NativeAdDetails>,
): List<CatalogCell> {
    if (items.isEmpty()) return emptyList()

    val positions = AdsDistribution.nativeAdPositions(items.size)
    if (positions.isEmpty() || ads.isEmpty()) {
        return items.map { CatalogCell.Content(it) }
    }

    // Asocia cada posición con un anuncio (como máximo hay tantas posiciones
    // como anuncios cargados).
    val adByPosition = positions.zip(ads).toMap()

    val cells = ArrayList<CatalogCell>(items.size + adByPosition.size)
    items.forEachIndexed { index, item ->
        adByPosition[index]?.let { ad -> cells.add(CatalogCell.Sponsored(ad, index)) }
        cells.add(CatalogCell.Content(item))
    }
    return cells
}
