package com.andrespar2.parflix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.andrespar2.parflix.data.local.entity.CatalogItemEntity
import kotlinx.coroutines.flow.Flow

/** Referencia ligera a un título ya existente (para preservar `addedAt` en cada sincronización). */
data class CatalogRef(
    val id: Long,
    val tmdbId: Int,
    val addedAt: Long,
    val syncedAt: Long?,
    val embedUrl: String?,
    val enriched: Boolean,
    val lastEnrichedAt: Long?,
    val title: String,
)

@Dao
interface CatalogDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNew(items: List<CatalogItemEntity>): List<Long>

    @Update
    suspend fun update(items: List<CatalogItemEntity>)

    @Query(
        """
        SELECT id, tmdbId, addedAt, syncedAt, embedUrl, enriched, lastEnrichedAt, title
        FROM catalog_items
        WHERE mediaType = :mediaType AND tmdbId IN (:tmdbIds)
        """
    )
    suspend fun refs(mediaType: String, tmdbIds: List<Int>): List<CatalogRef>

    @Query("SELECT * FROM catalog_items WHERE tmdbId = :tmdbId AND mediaType = :mediaType LIMIT 1")
    suspend fun find(tmdbId: Int, mediaType: String): CatalogItemEntity?

    @Query("SELECT * FROM catalog_items WHERE tmdbId = :tmdbId AND mediaType = :mediaType LIMIT 1")
    fun observe(tmdbId: Int, mediaType: String): Flow<CatalogItemEntity?>

    @Query("SELECT * FROM catalog_items WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<CatalogItemEntity?>

    @Query("SELECT * FROM catalog_items WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): CatalogItemEntity?

    @Query("SELECT id FROM catalog_items WHERE tmdbId = :tmdbId AND mediaType = :mediaType LIMIT 1")
    suspend fun idOf(tmdbId: Int, mediaType: String): Long?

    @Query(
        """
        SELECT * FROM catalog_items WHERE mediaType = :mediaType
        ORDER BY addedAt DESC LIMIT :limit OFFSET :offset
        """
    )
    fun observePage(mediaType: String, limit: Int, offset: Int): Flow<List<CatalogItemEntity>>

    @Query(
        """
        SELECT * FROM catalog_items WHERE mediaType = :mediaType
        ORDER BY addedAt DESC LIMIT :limit OFFSET :offset
        """
    )
    suspend fun page(mediaType: String, limit: Int, offset: Int): List<CatalogItemEntity>

    @Query(
        """
        SELECT * FROM catalog_items
        WHERE mediaType = :mediaType
        ORDER BY addedAt DESC LIMIT :limit
        """
    )
    fun observeRecent(mediaType: String, limit: Int): Flow<List<CatalogItemEntity>>

    @Query(
        """
        SELECT * FROM catalog_items
        WHERE voteAverage IS NOT NULL AND IFNULL(voteCount, 0) >= 10
        ORDER BY voteAverage DESC, IFNULL(voteCount, 0) DESC
        LIMIT :limit
        """
    )
    fun observeTopRated(limit: Int): Flow<List<CatalogItemEntity>>

    @Query(
        """
        SELECT * FROM catalog_items
        WHERE voteAverage IS NOT NULL AND IFNULL(voteCount, 0) >= 10
        AND mediaType = :mediaType
        ORDER BY voteAverage DESC, IFNULL(voteCount, 0) DESC
        LIMIT :limit
        """
    )
    fun observeTopRatedByType(mediaType: String, limit: Int): Flow<List<CatalogItemEntity>>

    /** Destacados: títulos con arte panorámico, ordenados por popularidad de TMDB. */
    @Query(
        """
        SELECT * FROM catalog_items
        WHERE backdropPath IS NOT NULL AND backdropPath != ''
        ORDER BY (popularity IS NULL), popularity DESC, addedAt DESC
        LIMIT :limit
        """
    )
    fun observeHighlights(limit: Int): Flow<List<CatalogItemEntity>>

    @Query(
        """
        SELECT c.* FROM catalog_items c
        JOIN genre_links g ON g.catalogId = c.id
        WHERE g.genreId = :genreId AND (:mediaType IS NULL OR c.mediaType = :mediaType)
        ORDER BY (c.popularity IS NULL), c.popularity DESC, c.voteAverage DESC
        LIMIT :limit
        """
    )
    fun observeByGenre(genreId: Int, mediaType: String?, limit: Int): Flow<List<CatalogItemEntity>>

    @Query("SELECT COUNT(*) FROM catalog_items WHERE mediaType = :mediaType")
    suspend fun count(mediaType: String): Int

    @Query("SELECT COUNT(*) FROM catalog_items WHERE mediaType = :mediaType")
    fun observeCount(mediaType: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM catalog_items")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM catalog_items WHERE enriched = 0")
    fun observePendingEnrichment(): Flow<Int>

    @Query("SELECT MAX(syncedAt) FROM catalog_items WHERE mediaType = :mediaType")
    suspend fun maxSyncedAt(mediaType: String): Long?

    @Query("UPDATE catalog_items SET lastSeenAt = :generation WHERE mediaType = :mediaType AND tmdbId IN (:tmdbIds)")
    suspend fun markSeen(mediaType: String, tmdbIds: List<Int>, generation: Long)

    /** Elimina lo que no se vio en el último barrido completo. Solo se llama tras un barrido íntegro. */
    @Query("DELETE FROM catalog_items WHERE mediaType = :mediaType AND lastSeenAt < :generation")
    suspend fun deleteStale(mediaType: String, generation: Long): Int

    @Query(
        """
        SELECT * FROM catalog_items WHERE enriched = 0
        AND mediaType = :mediaType
        ORDER BY addedAt DESC LIMIT :limit
        """
    )
    suspend fun needingEnrichment(mediaType: String, limit: Int): List<CatalogItemEntity>

    @Query(
        """
        SELECT * FROM catalog_items WHERE enriched = 0
        ORDER BY addedAt DESC LIMIT :limit
        """
    )
    suspend fun needingEnrichmentAnyType(limit: Int): List<CatalogItemEntity>

    @Query(
        """
        SELECT * FROM catalog_items
        WHERE enriched = 1 AND (lastEnrichedAt IS NULL OR lastEnrichedAt < :before)
        ORDER BY addedAt DESC LIMIT :limit
        """
    )
    suspend fun needingRefresh(before: Long, limit: Int): List<CatalogItemEntity>

    @Query(
        """
        UPDATE catalog_items SET
            enriched = 1,
            year = COALESCE(:year, year),
            voteAverage = COALESCE(:voteAverage, voteAverage),
            voteCount = COALESCE(:voteCount, voteCount),
            popularity = COALESCE(:popularity, popularity),
            overview = COALESCE(:overview, overview),
            totalSeasons = COALESCE(:totalSeasons, totalSeasons),
            totalEpisodes = COALESCE(:totalEpisodes, totalEpisodes),
            lastEnrichedAt = :now
        WHERE id = :id
        """
    )
    suspend fun applyEnrichment(
        id: Long,
        year: Int?,
        voteAverage: Double?,
        voteCount: Int?,
        popularity: Double?,
        overview: String?,
        totalSeasons: Int?,
        totalEpisodes: Int?,
        now: Long,
    )

    @Query("UPDATE catalog_items SET title = :title, titleNormalized = :normalized WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String, normalized: String)

    @Query("UPDATE catalog_items SET embedUrl = :embedUrl WHERE id = :id")
    suspend fun updateEmbedUrl(id: Long, embedUrl: String?)

    @Query("SELECT * FROM catalog_items WHERE imdbId = :imdbId LIMIT :limit")
    suspend fun byImdbId(imdbId: String, limit: Int): List<CatalogItemEntity>

    @Query("SELECT * FROM catalog_items WHERE tmdbId = :tmdbId LIMIT :limit")
    suspend fun byTmdbId(tmdbId: Int, limit: Int): List<CatalogItemEntity>

    @Query("SELECT * FROM catalog_items WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<CatalogItemEntity>

    @Query("DELETE FROM catalog_items")
    suspend fun clearAll()
}
