package com.andrespar2.parflix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.andrespar2.parflix.data.local.entity.CatalogFtsEntity
import com.andrespar2.parflix.data.local.entity.CatalogItemEntity

/** Candidato ligero para el respaldo de búsqueda tolerante a errores ortográficos. */
data class FuzzyCandidate(
    val rowId: Long,
    val names: String,
)

@Dao
interface CatalogFtsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<CatalogFtsEntity>)

    @Query("DELETE FROM catalog_fts WHERE rowid IN (:rowIds)")
    suspend fun deleteByRowIds(rowIds: List<Long>)

    @Query("DELETE FROM catalog_fts")
    suspend fun clear()

    /**
     * Búsqueda FTS4 (prefijos por término). `match` ya viene normalizado y con `*`.
     * El orden prioriza coincidencias por popularidad y valoración.
     */
    @Query(
        """
        SELECT c.* FROM catalog_fts
        JOIN catalog_items c ON c.id = catalog_fts.rowid
        WHERE catalog_fts MATCH :match AND (:mediaType IS NULL OR c.mediaType = :mediaType)
        ORDER BY (c.popularity IS NULL), c.popularity DESC, IFNULL(c.voteCount, 0) DESC
        LIMIT :limit
        """
    )
    suspend fun search(match: String, mediaType: String?, limit: Int): List<CatalogItemEntity>

    @Query(
        """
        SELECT catalog_fts.rowid AS rowId, names FROM catalog_fts
        LIMIT :limit
        """
    )
    suspend fun fuzzyCandidates(limit: Int): List<FuzzyCandidate>

    @Query(
        """
        SELECT rowid, title, originalTitle, altTitles, names, imdbId, tmdbId, year
        FROM catalog_fts WHERE rowid = :rowId LIMIT 1
        """
    )
    suspend fun row(rowId: Long): CatalogFtsEntity?

    @Query("DELETE FROM catalog_fts WHERE rowid NOT IN (SELECT id FROM catalog_items)")
    suspend fun deleteOrphans()

    @Query("SELECT COUNT(*) FROM catalog_fts")
    suspend fun count(): Int
}
