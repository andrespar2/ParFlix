package com.andrespar2.parflix.data.local

import com.andrespar2.parflix.data.local.entity.CatalogFtsEntity
import com.andrespar2.parflix.data.local.entity.CatalogItemEntity

/**
 * Escritura del catálogo respetando el estado local.
 *
 * Reglas importantes:
 * - `addedAt` nunca se sobrescribe: define el orden "agregados recientemente".
 * - Los datos de enriquecimiento (`enriched`, `lastEnrichedAt`, años, notas)
 *   no se pierden en una sincronización posterior.
 * - El índice FTS se actualiza sin destruir títulos alternativos ya conocidos.
 */
class CatalogWriter(private val database: ParFlixDatabase) {

    data class UpsertCounts(val inserted: Int, val updated: Int)

    suspend fun upsertItems(items: List<CatalogItemEntity>): UpsertCounts {
        if (items.isEmpty()) return UpsertCounts(0, 0)

        val byType = items.groupBy { it.mediaType }
        var inserted = 0
        var updated = 0

        byType.forEach { (mediaType, typeItems) ->
            val refs = database.catalogDao().refs(mediaType, typeItems.map { it.tmdbId })
            val existing = refs.associateBy { it.tmdbId }

            val toInsert = ArrayList<CatalogItemEntity>(typeItems.size)
            val toUpdate = ArrayList<CatalogItemEntity>(typeItems.size)

            typeItems.forEach { item ->
                val ref = existing[item.tmdbId]
                if (ref == null) {
                    toInsert += item
                } else {
                    toUpdate += item.copy(
                        id = ref.id,
                        addedAt = ref.addedAt,
                        embedUrl = item.embedUrl ?: ref.embedUrl,
                        syncedAt = item.syncedAt ?: ref.syncedAt,
                        enriched = ref.enriched,
                        lastEnrichedAt = ref.lastEnrichedAt,
                    )
                }
            }

            if (toInsert.isNotEmpty()) {
                database.catalogDao().insertNew(toInsert)
                inserted += toInsert.size
            }
            if (toUpdate.isNotEmpty()) {
                database.catalogDao().update(toUpdate)
                updated += toUpdate.size
            }
        }

        syncFtsRows(items)
        return UpsertCounts(inserted, updated)
    }

    /**
     * Actualiza el índice FTS de los títulos escritos. Si un título ya estaba
     * enriquecido, se conservan su título original y sus títulos alternativos.
     */
    private suspend fun syncFtsRows(items: List<CatalogItemEntity>) {
        val ftsDao = database.catalogFtsDao()
        val catalogDao = database.catalogDao()
        val rows = ArrayList<CatalogFtsEntity>(items.size)

        items.groupBy { it.mediaType }.forEach { (mediaType, typeItems) ->
            val refs = catalogDao.refs(mediaType, typeItems.map { it.tmdbId }).associateBy { it.tmdbId }
            typeItems.forEach { item ->
                val ref = refs[item.tmdbId] ?: return@forEach
                val previous = ftsDao.row(ref.id)
                rows += CatalogFtsEntity(
                    rowId = ref.id,
                    title = item.titleNormalized,
                    originalTitle = previous?.originalTitle.orEmpty(),
                    altTitles = previous?.altTitles.orEmpty(),
                    names = if (previous != null && item.enriched) {
                        previous.names
                    } else {
                        item.titleNormalized
                    },
                    imdbId = item.imdbId.orEmpty(),
                    tmdbId = item.tmdbId.toString(),
                    year = item.year?.toString() ?: previous?.year.orEmpty(),
                )
            }
        }
        if (rows.isNotEmpty()) ftsDao.upsert(rows)
    }

    /** Elimina títulos que ya no existen en el proveedor (solo tras un barrido completo). */
    suspend fun removeStale(mediaType: String, generation: Long): Int {
        val removed = database.catalogDao().deleteStale(mediaType, generation)
        if (removed > 0) {
            // Se reconstruye el índice FTS huérfano de forma segura.
            database.catalogFtsDao().deleteOrphans()
        }
        return removed
    }
}
