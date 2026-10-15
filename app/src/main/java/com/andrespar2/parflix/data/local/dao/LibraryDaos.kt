package com.andrespar2.parflix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.andrespar2.parflix.data.local.entity.FavoriteEntity
import com.andrespar2.parflix.data.local.entity.SearchQueryEntity
import com.andrespar2.parflix.data.local.entity.SyncPageEntity
import com.andrespar2.parflix.data.local.entity.SyncStateEntity
import com.andrespar2.parflix.data.local.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE tmdbId = :tmdbId AND mediaType = :mediaType")
    suspend fun delete(tmdbId: Int, mediaType: String)

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    suspend fun all(): List<FavoriteEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE tmdbId = :tmdbId AND mediaType = :mediaType)")
    fun observeExists(tmdbId: Int, mediaType: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE tmdbId = :tmdbId AND mediaType = :mediaType)")
    suspend fun exists(tmdbId: Int, mediaType: String): Boolean

    @Query("SELECT COUNT(*) FROM favorites")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun count(): Int
}

@Dao
interface HistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WatchHistoryEntity)

    @Query("SELECT * FROM watch_history ORDER BY lastWatchedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history ORDER BY lastWatchedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<WatchHistoryEntity>

    @Query("SELECT * FROM watch_history WHERE tmdbId = :tmdbId AND mediaType = :mediaType LIMIT 1")
    suspend fun find(tmdbId: Int, mediaType: String): WatchHistoryEntity?

    @Query("DELETE FROM watch_history WHERE tmdbId = :tmdbId AND mediaType = :mediaType")
    suspend fun delete(tmdbId: Int, mediaType: String)

    @Query("DELETE FROM watch_history")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM watch_history")
    fun observeCount(): Flow<Int>
}

@Dao
interface SearchQueryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SearchQueryEntity)

    @Query("SELECT * FROM search_queries ORDER BY lastUsedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SearchQueryEntity>>

    @Query("SELECT * FROM search_queries ORDER BY lastUsedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<SearchQueryEntity>

    @Query("DELETE FROM search_queries WHERE query = :query")
    suspend fun delete(query: String)

    @Query("DELETE FROM search_queries")
    suspend fun clear()

    /** Conserva solo las N búsquedas más recientes. */
    @Query(
        """
        DELETE FROM search_queries WHERE query NOT IN (
            SELECT query FROM search_queries ORDER BY lastUsedAt DESC LIMIT :keep
        )
        """
    )
    suspend fun trim(keep: Int)
}

@Dao
interface SyncDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putState(state: SyncStateEntity)

    @Query("SELECT * FROM sync_state WHERE key = :key LIMIT 1")
    suspend fun state(key: String): SyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPages(pages: List<SyncPageEntity>)

    @Query("SELECT * FROM sync_pages WHERE key = :key LIMIT 1")
    suspend fun page(key: String): SyncPageEntity?

    @Query("SELECT * FROM sync_pages")
    suspend fun allPages(): List<SyncPageEntity>

    @Query("DELETE FROM sync_pages")
    suspend fun clearPages()
}
