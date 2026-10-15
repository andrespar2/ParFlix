package com.andrespar2.parflix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.andrespar2.parflix.data.local.entity.AltTitleEntity
import com.andrespar2.parflix.data.local.entity.DetailsEntity
import com.andrespar2.parflix.data.local.entity.EpisodeEntity
import com.andrespar2.parflix.data.local.entity.GenreEntity
import com.andrespar2.parflix.data.local.entity.GenreLinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DetailsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(details: DetailsEntity)

    @Query("SELECT * FROM details WHERE tmdbId = :tmdbId AND mediaType = :mediaType LIMIT 1")
    suspend fun find(tmdbId: Int, mediaType: String): DetailsEntity?

    @Query("SELECT * FROM details WHERE tmdbId = :tmdbId AND mediaType = :mediaType LIMIT 1")
    fun observe(tmdbId: Int, mediaType: String): Flow<DetailsEntity?>

    @Query("SELECT COUNT(*) FROM details")
    suspend fun count(): Int
}

@Dao
interface AltTitleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(titles: List<AltTitleEntity>)

    @Query("SELECT * FROM alt_titles WHERE tmdbId = :tmdbId AND mediaType = :mediaType")
    suspend fun find(tmdbId: Int, mediaType: String): List<AltTitleEntity>

    @Query("SELECT * FROM alt_titles WHERE tmdbId = :tmdbId AND mediaType = :mediaType")
    fun observe(tmdbId: Int, mediaType: String): Flow<List<AltTitleEntity>>

    @Query("DELETE FROM alt_titles WHERE tmdbId = :tmdbId AND mediaType = :mediaType")
    suspend fun delete(tmdbId: Int, mediaType: String)
}

@Dao
interface EpisodeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(episodes: List<EpisodeEntity>)

    @Query(
        """
        SELECT * FROM episodes
        WHERE tmdbId = :tmdbId AND mediaType = :mediaType AND season = :season
        ORDER BY episode ASC
        """
    )
    fun observeSeason(tmdbId: Int, mediaType: String, season: Int): Flow<List<EpisodeEntity>>

    @Query(
        """
        SELECT * FROM episodes
        WHERE tmdbId = :tmdbId AND mediaType = :mediaType AND season = :season
        ORDER BY episode ASC
        """
    )
    suspend fun season(tmdbId: Int, mediaType: String, season: Int): List<EpisodeEntity>

    @Query(
        """
        SELECT COUNT(*) FROM episodes
        WHERE tmdbId = :tmdbId AND mediaType = :mediaType AND season = :season
        """
    )
    suspend fun countSeason(tmdbId: Int, mediaType: String, season: Int): Int

    @Query(
        """
        SELECT MAX(fetchedAt) FROM episodes
        WHERE tmdbId = :tmdbId AND mediaType = :mediaType AND season = :season
        """
    )
    suspend fun lastFetchedAt(tmdbId: Int, mediaType: String, season: Int): Long?

    @Query("SELECT COUNT(*) FROM episodes")
    suspend fun count(): Int

    /** Primer episodio reproducible (para el botón "Reproducir" de la ficha). */
    @Query(
        """
        SELECT * FROM episodes
        WHERE tmdbId = :tmdbId AND mediaType = :mediaType AND available = 1
        ORDER BY season ASC, episode ASC
        LIMIT 1
        """
    )
    suspend fun firstAvailable(tmdbId: Int, mediaType: String): EpisodeEntity?

    @Query("DELETE FROM episodes WHERE tmdbId = :tmdbId AND mediaType = :mediaType")
    suspend fun deleteForTitle(tmdbId: Int, mediaType: String)

    @Query("DELETE FROM episodes WHERE fetchedAt < :before")
    suspend fun deleteOlderThan(before: Long): Int
}

@Dao
interface GenreDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGenres(genres: List<GenreEntity>)

    @Query("SELECT * FROM genres WHERE mediaType = :mediaType ORDER BY name ASC")
    fun observeGenres(mediaType: String): Flow<List<GenreEntity>>

    @Query("SELECT * FROM genres WHERE mediaType = :mediaType ORDER BY name ASC")
    suspend fun genres(mediaType: String): List<GenreEntity>

    @Query("SELECT COUNT(*) FROM genres WHERE mediaType = :mediaType")
    suspend fun count(mediaType: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun linkGenres(links: List<GenreLinkEntity>)

    @Query("DELETE FROM genre_links WHERE catalogId = :catalogId")
    suspend fun clearLinks(catalogId: Long)

    @Query("SELECT genreId FROM genre_links WHERE catalogId = :catalogId")
    suspend fun genreIdsOf(catalogId: Long): List<Int>
}
