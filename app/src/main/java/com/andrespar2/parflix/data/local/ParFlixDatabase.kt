package com.andrespar2.parflix.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.andrespar2.parflix.data.local.dao.AltTitleDao
import com.andrespar2.parflix.data.local.dao.CatalogDao
import com.andrespar2.parflix.data.local.dao.CatalogFtsDao
import com.andrespar2.parflix.data.local.dao.DetailsDao
import com.andrespar2.parflix.data.local.dao.EpisodeDao
import com.andrespar2.parflix.data.local.dao.FavoriteDao
import com.andrespar2.parflix.data.local.dao.GenreDao
import com.andrespar2.parflix.data.local.dao.HistoryDao
import com.andrespar2.parflix.data.local.dao.SearchQueryDao
import com.andrespar2.parflix.data.local.dao.SyncDao
import com.andrespar2.parflix.data.local.entity.AltTitleEntity
import com.andrespar2.parflix.data.local.entity.CatalogFtsEntity
import com.andrespar2.parflix.data.local.entity.CatalogItemEntity
import com.andrespar2.parflix.data.local.entity.DetailsEntity
import com.andrespar2.parflix.data.local.entity.EpisodeEntity
import com.andrespar2.parflix.data.local.entity.FavoriteEntity
import com.andrespar2.parflix.data.local.entity.GenreEntity
import com.andrespar2.parflix.data.local.entity.GenreLinkEntity
import com.andrespar2.parflix.data.local.entity.SearchQueryEntity
import com.andrespar2.parflix.data.local.entity.SyncPageEntity
import com.andrespar2.parflix.data.local.entity.SyncStateEntity
import com.andrespar2.parflix.data.local.entity.WatchHistoryEntity

/**
 * Base de datos local de ParFlix. Cada dispositivo mantiene la suya, sin
 * servidores ni sincronización entre teléfonos.
 */
@Database(
    entities = [
        CatalogItemEntity::class,
        CatalogFtsEntity::class,
        AltTitleEntity::class,
        DetailsEntity::class,
        EpisodeEntity::class,
        FavoriteEntity::class,
        WatchHistoryEntity::class,
        SearchQueryEntity::class,
        GenreEntity::class,
        GenreLinkEntity::class,
        SyncStateEntity::class,
        SyncPageEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class ParFlixDatabase : RoomDatabase() {

    abstract fun catalogDao(): CatalogDao
    abstract fun catalogFtsDao(): CatalogFtsDao
    abstract fun detailsDao(): DetailsDao
    abstract fun altTitleDao(): AltTitleDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun searchQueryDao(): SearchQueryDao
    abstract fun genreDao(): GenreDao
    abstract fun syncDao(): SyncDao

    companion object {
        fun build(context: Context): ParFlixDatabase = Room
            .databaseBuilder(context.applicationContext, ParFlixDatabase::class.java, "parflix.db")
            .build()
    }
}
