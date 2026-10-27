package com.andrespar2.parflix.ui.screens.catalog

import com.andrespar2.parflix.ads.AdsDistribution
import com.andrespar2.parflix.data.local.entity.CatalogItemEntity

/**
 * Elemento de la cuadrícula del catálogo: contenido real o espacio nativo.
 *
 * Mantener las claves estables de las tarjetas reales evita que Compose
 * recree elementos al insertar publicidad. Los espacios nativos usan su propia
 * clave, que nunca puede chocar con la de un título.
 */
sealed interface CatalogCell {
    val key: String

    data class Content(
        val item: CatalogItemEntity,
    ) : CatalogCell {
        override val key: String = "${item.mediaType}:${item.tmdbId}"
    }

    data class Sponsored(
        val position: Int,
    ) : CatalogCell {
        override val key: String = "sponsored:$position"
    }
}

/**
 * Intercala espacios nativos en la lista real según [AdsDistribution].
 *
 * [sponsoredCount] es el número de creatividades realmente listas. Si es 0 no
 * se inserta ningún hueco; el catálogo se ve exactamente igual que sin
 * publicidad. Nunca se reordena ni se altera el contenido.
 */
fun buildCatalogCells(
    items: List<CatalogItemEntity>,
    sponsoredCount: Int,
): List<CatalogCell> {
    if (items.isEmpty() || sponsoredCount <= 0) {
        return items.map { CatalogCell.Content(it) }
    }

    val positions = AdsDistribution.nativeAdPositions(items.size)
        .take(sponsoredCount)
    if (positions.isEmpty()) return items.map { CatalogCell.Content(it) }

    val sponsoredAt = positions.toSet()
    val cells = ArrayList<CatalogCell>(items.size + sponsoredAt.size)
    items.forEachIndexed { index, item ->
        if (index in sponsoredAt) cells.add(CatalogCell.Sponsored(position = index))
        cells.add(CatalogCell.Content(item))
    }
    return cells
}
