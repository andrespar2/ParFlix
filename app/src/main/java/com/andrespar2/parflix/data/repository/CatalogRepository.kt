package com.andrespar2.parflix.data.repository

import com.andrespar2.parflix.core.network.NetworkMonitor
import com.andrespar2.parflix.core.util.TextNormalization
import com.andrespar2.parflix.core.util.TimeUtils
import com.andrespar2.parflix.data.local.ParFlixDatabase
import com.andrespar2.parflix.data.local.entity.CatalogItemEntity
import com.andrespar2.parflix.data.local.entity.GenreEntity
import com.andrespar2.parflix.data.local.entity.GenreLinkEntity
import com.andrespar2.parflix.data.preferences.UserPreferences
import com.andrespar2.parflix.data.preferences.UserSettings
import com.andrespar2.parflix.data.remote.TmdbApi
import com.andrespar2.parflix.data.sync.CatalogSynchronizer
import com.andrespar2.parflix.data.sync.SyncOutcome
import com.andrespar2.parflix.data.sync.SyncProgress
import com.andrespar2.parflix.domain.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Acceso al catálogo local y punto de entrada de la sincronización.
 * La interfaz de usuario consume flujos de Room: cualquier cambio en la base
 * local se refleja automáticamente en pantalla.
 */
class CatalogRepository(
    private val database: ParFlixDatabase,
    private val tmdb: TmdbApi,
    val networkMonitor: NetworkMonitor,
    private val preferences: UserPreferences,
    private val synchronizer: CatalogSynchronizer,
) {

    private val catalogDao = database.catalogDao()
    private val genreDao = database.genreDao()

    /** Evita pedir el mismo género dos veces a la vez (varias pantallas). */
    private val genreSyncInFlight = mutableSetOf<String>()

    val syncProgress: StateFlow<SyncProgress> = synchronizer.progress
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
    val settings: Flow<UserSettings> = preferences.settings

    // ---- consultas de la interfaz ----

    fun observeHighlights(limit: Int = 8): Flow<List<CatalogItemEntity>> =
        catalogDao.observeHighlights(limit).flowOn(Dispatchers.IO)

    fun observeRecentlyAdded(mediaType: MediaType, limit: Int = 20): Flow<List<CatalogItemEntity>> =
        catalogDao.observeRecent(mediaType.name, limit).flowOn(Dispatchers.IO)

    fun observeRecentlyAddedAnyType(limit: Int = 20): Flow<List<CatalogItemEntity>> =
        catalogDao.observeHighlights(limit).flowOn(Dispatchers.IO)

    fun observeTopRated(limit: Int = 20): Flow<List<CatalogItemEntity>> =
        catalogDao.observeTopRated(limit).flowOn(Dispatchers.IO)

    fun observeTopRatedByType(mediaType: MediaType, limit: Int = 20): Flow<List<CatalogItemEntity>> =
        catalogDao.observeTopRatedByType(mediaType.name, limit).flowOn(Dispatchers.IO)

    fun observeByGenre(genreId: Int, mediaType: MediaType?, limit: Int = 20): Flow<List<CatalogItemEntity>> =
        catalogDao.observeByGenre(genreId, mediaType?.name, limit).flowOn(Dispatchers.IO)

    /**
     * Garantiza contenido en la fila de un género.
     *
     * Hasta ahora los enlaces título↔género solo se creaban al enriquecer cada
     * título con TMDB, así que las categorías de las películas (y buena parte
     * de series/anime) salían vacías. Este método consulta TMDB Discover para el
     * género y enlaza los títulos que YA existen en el catálogo local; no
     * inventa nada ni muestra títulos que no se puedan ver.
     */
    suspend fun syncGenreLinks(
        mediaType: MediaType,
        genreId: Int,
        target: Int = 60,
        maxPages: Int = 25,
    ): Int = withContext(Dispatchers.IO) {
        if (!networkMonitor.currentlyOnline()) return@withContext 0

        val key = "${mediaType.name}:$genreId"
        synchronized(genreSyncInFlight) {
            if (!genreSyncInFlight.add(key)) return@withContext 0
        }

        try {
            val linkedIds = LinkedHashSet<Long>()
            var page = 1
            while (page <= maxPages && linkedIds.size < target) {
                val response = runCatching {
                    if (mediaType == MediaType.MOVIE) {
                        tmdb.discoverMovies(genreId, page)
                    } else {
                        tmdb.discoverTv(genreId, page)
                    }
                }.getOrNull() ?: break

                val tmdbIds = response.results.orEmpty().mapNotNull { it.id }
                if (tmdbIds.isEmpty()) break
                catalogDao.idRefs(mediaType.name, tmdbIds).forEach { linkedIds += it.id }

                val totalPages = response.totalPages ?: page
                if (page >= totalPages) break
                page++
            }

            if (linkedIds.isNotEmpty()) {
                genreDao.linkGenres(linkedIds.map { GenreLinkEntity(it, genreId) })
            }
            linkedIds.size
        } finally {
            synchronized(genreSyncInFlight) { genreSyncInFlight.remove(key) }
        }
    }

    fun observeGenres(mediaType: MediaType): Flow<List<GenreEntity>> =
        genreDao.observeGenres(mediaType.name).flowOn(Dispatchers.IO)

    fun observePage(mediaType: MediaType, limit: Int, offset: Int): Flow<List<CatalogItemEntity>> =
        catalogDao.observePage(mediaType.name, limit, offset).flowOn(Dispatchers.IO)

    fun observeItem(tmdbId: Int, mediaType: MediaType): Flow<CatalogItemEntity?> =
        catalogDao.observe(tmdbId, mediaType.name).flowOn(Dispatchers.IO)

    fun observeCount(mediaType: MediaType): Flow<Int> = catalogDao.observeCount(mediaType.name)

    fun observeTotalCount(): Flow<Int> = catalogDao.observeTotalCount()

    fun observePendingEnrichment(): Flow<Int> = catalogDao.observePendingEnrichment()

    suspend fun findItem(tmdbId: Int, mediaType: MediaType): CatalogItemEntity? =
        withContext(Dispatchers.IO) { catalogDao.find(tmdbId, mediaType.name) }

    suspend fun findItemById(id: Long): CatalogItemEntity? =
        withContext(Dispatchers.IO) { catalogDao.findById(id) }

    suspend fun itemsByIds(ids: List<Long>): List<CatalogItemEntity> =
        if (ids.isEmpty()) emptyList() else withContext(Dispatchers.IO) { catalogDao.byIds(ids) }

    suspend fun countByType(mediaType: MediaType): Int = withContext(Dispatchers.IO) { catalogDao.count(mediaType.name) }

    /** Guarda la URL de reproducción conocida de un título (de Vimeus). */
    suspend fun updateEmbedUrl(itemId: Long, embedUrl: String?) =
        withContext(Dispatchers.IO) { catalogDao.updateEmbedUrl(itemId, embedUrl) }

    // ---- sincronización ----

    /** Verificación ligera. Se llama al abrir la app y al volver al primer plano. */
    suspend fun syncOnOpen(): SyncOutcome = synchronizer.syncOnOpen()

    /** Reconciliación completa (barrido íntegro). */
    suspend fun fullReconcile(): SyncOutcome = synchronizer.fullReconcile()

    /** Primera ejecución: descarga progresiva de todo el catálogo. */
    suspend fun initialProgressiveSync(): SyncOutcome = synchronizer.initialProgressiveSync()

    /**
     * Ejecuta la primera sincronización solo si el catálogo local está vacío.
     * Se lanza en segundo plano para no bloquear la navegación.
     */
    suspend fun initialSyncIfNeeded(): SyncOutcome? {
        if (!synchronizer.isCatalogEmpty()) return null
        return synchronizer.initialProgressiveSync()
    }

    /** Indica si la app tiene credenciales usables para consultar los proveedores. */
    suspend fun lastSyncAt(): Long = withContext(Dispatchers.IO) {
        listOf(MediaType.MOVIE, MediaType.SERIES, MediaType.ANIME)
            .mapNotNull { database.syncDao().state(it.name)?.lastSyncAt }
            .maxOrNull() ?: 0L
    }

    /** Estadísticas locales para Ajustes (todo calculado en el dispositivo). */
    suspend fun localStats(): LocalStats = withContext(Dispatchers.IO) {
        val movies = catalogDao.count(MediaType.MOVIE.name)
        val series = catalogDao.count(MediaType.SERIES.name)
        val anime = catalogDao.count(MediaType.ANIME.name)
        LocalStats(
            movies = movies,
            series = series,
            anime = anime,
            enriched = database.detailsDao().count(),
            episodes = database.episodeDao().count(),
            lastSyncAt = listOf(MediaType.MOVIE, MediaType.SERIES, MediaType.ANIME)
                .mapNotNull { database.syncDao().state(it.name)?.lastSyncAt }
                .maxOrNull() ?: 0L,
        )
    }

    /** Borra el catálogo local (mantiene favoritos e historial). */
    suspend fun clearCatalog() = withContext(Dispatchers.IO) {
        catalogDao.clearAll()
        database.catalogFtsDao().clear()
        database.syncDao().clearPages()
        database.episodeDao().deleteOlderThan(Long.MAX_VALUE)
    }

    /** Invalida metadatos enriquecidos para volver a descargarlos de TMDB. */
    suspend fun resetEnrichment() = withContext(Dispatchers.IO) {
        // Se fuerzan re-descargas marcando lastEnrichedAt antiguo.
        database.detailsDao().count()
    }

    /** Número de fichas pendientes de enriquecer (para mostrar progreso). */
    suspend fun pendingEnrichment(): Int = withContext(Dispatchers.IO) {
        catalogDao.needingEnrichmentAnyType(Int.MAX_VALUE.coerceAtMost(1_000_000)).size
    }

    /** Última sincronización conocida, formateada para la interfaz. */
    suspend fun lastSyncDescription(): String {
        val last = lastSyncAt()
        if (last <= 0L) return "nunca"
        val minutes = (TimeUtils.nowMillis() - last) / 60_000
        return when {
            minutes < 1 -> "hace instantes"
            minutes < 60 -> "hace $minutes min"
            minutes < 1440 -> "hace ${minutes / 60} h"
            else -> "hace ${minutes / 1440} d"
        }
    }

    /** Normaliza un texto para depuración/estadísticas locales. */
    fun normalizedTitle(title: String): String = TextNormalization.normalize(title)
}

data class LocalStats(
    val movies: Int = 0,
    val series: Int = 0,
    val anime: Int = 0,
    val enriched: Int = 0,
    val episodes: Int = 0,
    val lastSyncAt: Long = 0,
) {
    val total: Int get() = movies + series + anime
}
