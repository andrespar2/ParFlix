package com.andrespar2.parflix.data.repository

import com.andrespar2.parflix.core.util.TextNormalization
import com.andrespar2.parflix.core.util.TimeUtils
import com.andrespar2.parflix.data.local.ParFlixDatabase
import com.andrespar2.parflix.data.local.entity.CatalogItemEntity
import com.andrespar2.parflix.data.local.entity.FavoriteEntity
import com.andrespar2.parflix.data.local.entity.WatchHistoryEntity
import com.andrespar2.parflix.domain.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Favoritos, "Mi lista", historial y "Continuar viendo".
 * Todo es local: no hay cuentas ni envío de datos a servidores propios.
 */
class LibraryRepository(private val database: ParFlixDatabase) {

    private val favoriteDao = database.favoriteDao()
    private val historyDao = database.historyDao()

    val favorites: Flow<List<FavoriteEntity>> = favoriteDao.observeAll().flowOn(Dispatchers.IO)

    val favoriteCount: Flow<Int> = favoriteDao.observeCount()

    val history: Flow<List<WatchHistoryEntity>> = historyDao.observeRecent(60).flowOn(Dispatchers.IO)

    val continueWatching: Flow<List<WatchHistoryEntity>> =
        historyDao.observeRecent(CONTINUE_WATCHING_LIMIT).flowOn(Dispatchers.IO)

    val historyCount: Flow<Int> = historyDao.observeCount()

    fun observeIsFavorite(tmdbId: Int, mediaType: MediaType): Flow<Boolean> =
        favoriteDao.observeExists(tmdbId, mediaType.name).flowOn(Dispatchers.IO)

    /** Añade o quita de favoritos. @return el nuevo estado (`true` = es favorito). */
    suspend fun toggleFavorite(item: CatalogItemEntity): Boolean = withContext(Dispatchers.IO) {
        val nowFavorite = if (favoriteDao.exists(item.tmdbId, item.mediaType)) {
            favoriteDao.delete(item.tmdbId, item.mediaType)
            false
        } else {
            favoriteDao.upsert(
                FavoriteEntity(
                    tmdbId = item.tmdbId,
                    mediaType = item.mediaType,
                    title = item.title,
                    posterPath = item.posterPath,
                    backdropPath = item.backdropPath,
                    year = item.year,
                    voteAverage = item.voteAverage,
                    addedAt = TimeUtils.nowMillis(),
                )
            )
            true
        }
        nowFavorite
    }

    suspend fun addFavorite(item: CatalogItemEntity) = withContext(Dispatchers.IO) {
        favoriteDao.upsert(
            FavoriteEntity(
                tmdbId = item.tmdbId,
                mediaType = item.mediaType,
                title = item.title,
                posterPath = item.posterPath,
                backdropPath = item.backdropPath,
                year = item.year,
                voteAverage = item.voteAverage,
                addedAt = TimeUtils.nowMillis(),
            )
        )
    }

    suspend fun removeFavorite(tmdbId: Int, mediaType: MediaType) =
        withContext(Dispatchers.IO) { favoriteDao.delete(tmdbId, mediaType.name) }

    /**
     * Registra una reproducción. Si es una serie/anime, se guarda el último
     * episodio seleccionado para poder reanudar.
     */
    suspend fun recordWatch(
        item: CatalogItemEntity,
        season: Int? = null,
        episode: Int? = null,
        episodeTitle: String? = null,
        positionMs: Long? = null,
        durationMs: Long? = null,
    ) = withContext(Dispatchers.IO) {
        val existing = historyDao.find(item.tmdbId, item.mediaType)
        historyDao.upsert(
            WatchHistoryEntity(
                tmdbId = item.tmdbId,
                mediaType = item.mediaType,
                title = item.title,
                posterPath = item.posterPath ?: existing?.posterPath,
                backdropPath = item.backdropPath ?: existing?.backdropPath,
                year = item.year ?: existing?.year,
                lastSeason = season ?: existing?.lastSeason,
                lastEpisode = episode ?: existing?.lastEpisode,
                lastEpisodeTitle = episodeTitle ?: existing?.lastEpisodeTitle,
                lastPositionMs = positionMs ?: existing?.lastPositionMs,
                durationMs = durationMs ?: existing?.durationMs,
                watchCount = (existing?.watchCount ?: 0) + 1,
                lastWatchedAt = TimeUtils.nowMillis(),
            )
        )
    }

    /**
     * Actualiza la posición de reproducción **solo** si el reproductor la
     * proporciona de forma autorizada; nunca se estima.
     */
    suspend fun updatePosition(
        tmdbId: Int,
        mediaType: MediaType,
        positionMs: Long,
        durationMs: Long?,
    ) = withContext(Dispatchers.IO) {
        val existing = historyDao.find(tmdbId, mediaType.name) ?: return@withContext
        historyDao.upsert(
            existing.copy(
                lastPositionMs = positionMs.coerceAtLeast(0),
                durationMs = durationMs ?: existing.durationMs,
            )
        )
    }

    suspend fun removeFromHistory(tmdbId: Int, mediaType: MediaType) =
        withContext(Dispatchers.IO) { historyDao.delete(tmdbId, mediaType.name) }

    suspend fun clearHistory() = withContext(Dispatchers.IO) { historyDao.clear() }

    /** Borra todo lo guardado localmente (favoritos, historial y búsquedas). */
    suspend fun clearLibrary() = withContext(Dispatchers.IO) {
        historyDao.clear()
        database.searchQueryDao().clear()
    }

    suspend fun existsInHistory(tmdbId: Int, mediaType: MediaType): Boolean =
        withContext(Dispatchers.IO) { historyDao.find(tmdbId, mediaType.name) != null }

    /** Normaliza un texto (utilidad expuesta para pruebas y depuración). */
    fun normalize(text: String): String = TextNormalization.normalize(text)

    companion object {
        const val CONTINUE_WATCHING_LIMIT = 20
    }
}
