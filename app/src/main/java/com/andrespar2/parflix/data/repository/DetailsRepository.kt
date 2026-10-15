package com.andrespar2.parflix.data.repository

import com.andrespar2.parflix.core.network.ApiResult
import com.andrespar2.parflix.core.network.apiCall
import com.andrespar2.parflix.core.util.TextNormalization
import com.andrespar2.parflix.core.util.TimeUtils
import com.andrespar2.parflix.data.local.ParFlixDatabase
import com.andrespar2.parflix.data.local.entity.CatalogItemEntity
import com.andrespar2.parflix.data.local.entity.EpisodeEntity
import com.andrespar2.parflix.data.local.entity.GenreEntity
import com.andrespar2.parflix.data.local.entity.GenreLinkEntity
import com.andrespar2.parflix.data.local.model.JsonStore
import com.andrespar2.parflix.data.remote.TmdbApi
import com.andrespar2.parflix.data.remote.VimeusApi
import com.andrespar2.parflix.data.remote.mapper.TmdbMappers
import com.andrespar2.parflix.data.remote.mapper.VimeusMappers
import com.andrespar2.parflix.domain.MediaDetailsView
import com.andrespar2.parflix.domain.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Metadatos detallados: enriquecimiento progresivo con TMDB y episodios
 * bajo demanda (TMDB + disponibilidad real de Vimeus).
 *
 * Principios de eficiencia:
 * - No se consultan miles de fichas a la vez: hay una puerta de concurrencia.
 * - Las fichas abiertas se enriquecen al momento; el resto, poco a poco.
 * - Los episodios solo se piden para la temporada que el usuario abre y se
 *   reutilizan durante un periodo de caché.
 */
class DetailsRepository(
    private val database: ParFlixDatabase,
    private val tmdb: TmdbApi,
    private val vimeus: VimeusApi,
) {

    private val detailsDao = database.detailsDao()
    private val altTitleDao = database.altTitleDao()
    private val episodeDao = database.episodeDao()
    private val catalogDao = database.catalogDao()
    private val ftsDao = database.catalogFtsDao()
    private val genreDao = database.genreDao()

    /** Límite de peticiones simultáneas a TMDB (respeto de límites del proveedor). */
    private val tmdbGate = Semaphore(3)

    /** Ventana de reutilización de episodios ya descargados. */
    private val episodeTtlMs = 24 * 60 * 60 * 1000L

    fun observeDetails(tmdbId: Int, mediaType: MediaType): Flow<MediaDetailsView?> {
        val itemFlow = catalogDao.observe(tmdbId, mediaType.name)
        val detailsFlow = detailsDao.observe(tmdbId, mediaType.name)
        val altFlow = altTitleDao.observe(tmdbId, mediaType.name)
        return combine(itemFlow, detailsFlow, altFlow) { item, details, alts ->
            if (item == null) return@combine null
            MediaDetailsView(
                item = item,
                details = details,
                genres = JsonStore.decodeGenres(details?.genresJson),
                cast = JsonStore.decodePeople(details?.castJson),
                crew = JsonStore.decodePeople(details?.crewJson),
                companies = JsonStore.decodeCompanies(details?.companiesJson) +
                    JsonStore.decodeCompanies(details?.networksJson),
                countries = JsonStore.decodeCountries(details?.countriesJson),
                languages = JsonStore.decodeLanguages(details?.languagesJson),
                videos = JsonStore.decodeVideos(details?.videosJson),
                recommendations = JsonStore.decodeRecommendations(details?.recommendationsJson),
                seasons = JsonStore.decodeSeasons(details?.seasonsJson),
                altTitles = alts.map { it.title },
            )
        }.flowOn(Dispatchers.Default)
    }

    fun observeSeasonEpisodes(tmdbId: Int, mediaType: MediaType, season: Int): Flow<List<EpisodeEntity>> =
        episodeDao.observeSeason(tmdbId, mediaType.name, season)

    /** Un episodio concreto desde la caché local (para reproducir). */
    suspend fun findEpisode(
        tmdbId: Int,
        mediaType: MediaType,
        season: Int,
        episode: Int,
    ): EpisodeEntity? = withContext(Dispatchers.IO) {
        episodeDao.season(tmdbId, mediaType.name, season).firstOrNull { it.episode == episode }
    }

    /**
     * Primer episodio reproducible de una serie o anime. Se usa para el botón
     * "Reproducir" de la ficha, que no conoce temporada ni episodio.
     *
     * Se resuelve en local y, si aún no hay episodios conocidos, se pide la
     * primera temporada al proveedor. Devuelve null si no hay nada reproducible.
     */
    suspend fun firstPlayableEpisode(tmdbId: Int, mediaType: MediaType): Pair<Int, Int>? {
        if (!mediaType.isShow) return null
        withContext(Dispatchers.IO) { episodeDao.firstAvailable(tmdbId, mediaType.name) }
            ?.let { return it.season to it.episode }
        runCatching { ensureSeason(tmdbId, mediaType, 1) }
        return withContext(Dispatchers.IO) { episodeDao.firstAvailable(tmdbId, mediaType.name) }
            ?.let { it.season to it.episode }
    }

    /** @return true si la ficha quedó enriquecida con metadatos de TMDB. */
    suspend fun enrich(item: CatalogItemEntity, force: Boolean = false): Boolean {
        val type = item.type ?: return false
        if (!force && item.enriched && item.lastEnrichedAt != null &&
            TimeUtils.nowMillis() - item.lastEnrichedAt < ENRICH_TTL_MS
        ) {
            return true
        }
        val now = TimeUtils.nowMillis()
        val result = tmdbGate.withPermit {
            apiCall {
                when (type) {
                    MediaType.MOVIE -> {
                        val dto = tmdb.movie(item.tmdbId)
                        Triple(
                            TmdbMappers.detailsFromMovie(dto, type, now),
                            TmdbMappers.altTitles(
                                tmdbId = item.tmdbId,
                                mediaType = type,
                                altTitles = dto.alternativeTitles?.titles.orEmpty().map { it.iso3166 to it.title },
                                translations = dto.translations?.translations.orEmpty().map {
                                    Triple(it.iso639, it.data?.title ?: it.data?.name, it.iso3166)
                                },
                            ),
                            dto.originalTitle ?: dto.title,
                        )
                    }
                    else -> {
                        val dto = tmdb.tv(item.tmdbId)
                        Triple(
                            TmdbMappers.detailsFromTv(dto, type, now),
                            TmdbMappers.altTitles(
                                tmdbId = item.tmdbId,
                                mediaType = type,
                                altTitles = dto.alternativeTitles?.titles.orEmpty().map { it.iso3166 to it.title },
                                translations = dto.translations?.translations.orEmpty().map {
                                    Triple(it.iso639, it.data?.name ?: it.data?.title, it.iso3166)
                                },
                            ),
                            dto.originalName ?: dto.name,
                        )
                    }
                }
            }
        }

        return when (result) {
            is ApiResult.Success -> {
                val (details, altTitles, originalTitle) = result.value
                withContext(Dispatchers.IO) {
                    persistEnrichment(item, details, altTitles, originalTitle)
                }
                true
            }
            else -> false
        }
    }

    private suspend fun persistEnrichment(
        item: CatalogItemEntity,
        details: com.andrespar2.parflix.data.local.entity.DetailsEntity,
        altTitles: List<com.andrespar2.parflix.data.local.entity.AltTitleEntity>,
        originalTitle: String?,
    ) {
        detailsDao.upsert(details)
        altTitleDao.delete(item.tmdbId, item.mediaType)
        if (altTitles.isNotEmpty()) altTitleDao.upsertAll(altTitles)

        val year = TimeUtils.yearOf(details.releaseDate ?: details.firstAirDate)
        catalogDao.applyEnrichment(
            id = item.id,
            year = year,
            voteAverage = details.voteAverage,
            voteCount = details.voteCount,
            popularity = details.popularity,
            overview = details.overview,
            totalSeasons = details.numberOfSeasons,
            totalEpisodes = details.numberOfEpisodes,
            now = TimeUtils.nowMillis(),
        )

        // Relación con géneros (permite filas y filtros por género).
        val genres = JsonStore.decodeGenres(details.genresJson)
        genreDao.clearLinks(item.id)
        if (genres.isNotEmpty()) {
            genreDao.linkGenres(genres.map { GenreLinkEntity(item.id, it.id) })
        }

        // Índice de búsqueda con título original y alternativos.
        val updated = item.copy(
            year = year ?: item.year,
            enriched = true,
            lastEnrichedAt = TimeUtils.nowMillis(),
        )
        ftsDao.upsert(
            listOf(
                VimeusMappers.toFtsRow(
                    item = updated,
                    originalTitle = originalTitle,
                    altTitles = altTitles.map { it.title },
                    year = year,
                )
            )
        )
    }

    /** Enriquece hasta [limit] fichas pendientes, priorizando las más recientes. */
    suspend fun enrichPending(limit: Int): Int {
        val pending = withContext(Dispatchers.IO) { catalogDao.needingEnrichmentAnyType(limit) }
        var enriched = 0
        for (item in pending) {
            if (enrich(item)) enriched++
        }
        return enriched
    }

    /**
     * Carga los episodios de una temporada combinando TMDB (metadatos) y Vimeus
     * (disponibilidad real). Reutiliza la caché si es reciente.
     */
    suspend fun ensureSeason(
        tmdbId: Int,
        mediaType: MediaType,
        season: Int,
        force: Boolean = false,
    ): Boolean {
        if (!mediaType.isShow) return false
        val now = TimeUtils.nowMillis()
        val lastFetched = withContext(Dispatchers.IO) { episodeDao.lastFetchedAt(tmdbId, mediaType.name, season) }
        if (!force && lastFetched != null && now - lastFetched < episodeTtlMs) return true

        val vimeusEpisodes = fetchVimeusSeason(tmdbId, mediaType, season)
        val tmdbSeason = tmdbGate.withPermit {
            apiCall { tmdb.season(tmdbId, season) }
        }

        val tmdbEpisodes = when (tmdbSeason) {
            is ApiResult.Success -> tmdbSeason.value.episodes.orEmpty()
            else -> emptyList()
        }
        if (vimeusEpisodes == null && tmdbEpisodes.isEmpty()) return false

        val existing = withContext(Dispatchers.IO) { episodeDao.season(tmdbId, mediaType.name, season) }
        val existingByNumber = existing.associateBy { it.episode }
        val availableNumbers = vimeusEpisodes?.keys
        val vimeusByNumber = vimeusEpisodes.orEmpty()

        val merged = LinkedHashMap<Int, EpisodeEntity>()

        tmdbEpisodes.forEach { ep ->
            val number = ep.episodeNumber ?: return@forEach
            val vimeusEp = vimeusByNumber[number]
            val previous = existingByNumber[number]
            val available = when {
                vimeusEp != null -> true
                availableNumbers != null -> false          // Vimeus respondió y no lo tiene
                else -> previous?.available ?: false       // Vimeus falló: se conserva lo conocido
            }
            merged[number] = EpisodeEntity(
                id = previous?.id ?: 0,
                tmdbId = tmdbId,
                mediaType = mediaType.name,
                season = season,
                episode = number,
                title = ep.name?.takeIf { it.isNotBlank() } ?: previous?.title,
                overview = ep.overview?.takeIf { it.isNotBlank() } ?: previous?.overview,
                runtimeMinutes = ep.runtime?.takeIf { it > 0 } ?: previous?.runtimeMinutes,
                airDate = ep.airDate ?: previous?.airDate,
                stillPath = ep.stillPath ?: previous?.stillPath,
                voteAverage = ep.voteAverage ?: previous?.voteAverage,
                embedUrl = vimeusEp?.embedUrl ?: previous?.embedUrl,
                available = available,
                fromVimeus = vimeusEp != null || (previous?.fromVimeus == true),
                fromTmdb = true,
                fetchedAt = now,
            )
        }

        // Episodios presentes solo en Vimeus (TMDB aún no los publica).
        vimeusByNumber.forEach { (number, ep) ->
            if (!merged.containsKey(number)) {
                merged[number] = ep.copy(id = existingByNumber[number]?.id ?: 0, fetchedAt = now)
            }
        }

        withContext(Dispatchers.IO) { episodeDao.upsertAll(merged.values.toList()) }
        return true
    }

    /** @return mapa episodio→entidad, o null si Vimeus falló (no se debe borrar información). */
    private suspend fun fetchVimeusSeason(
        tmdbId: Int,
        mediaType: MediaType,
        season: Int,
    ): Map<Int, EpisodeEntity>? {
        val now = TimeUtils.nowMillis()
        val collected = LinkedHashMap<Int, EpisodeEntity>()
        var page = 1
        var totalPages = 1
        do {
            val result = apiCall(maxAttempts = 2) { vimeus.episodes(tmdbId = tmdbId, season = season, page = page) }
            when (result) {
                is ApiResult.Success -> {
                    val data = result.value.data
                    totalPages = data?.pages?.coerceAtLeast(1) ?: 1
                    data?.result.orEmpty().forEach { dto ->
                        val episode = VimeusMappers.toEpisode(dto, mediaType, now) ?: return@forEach
                        collected[episode.episode] = episode
                    }
                }
                else -> return null
            }
            page++
        } while (page <= totalPages && page <= MAX_EPISODE_PAGES)

        return collected
    }

    /** Cachea los nombres de géneros de TMDB (una sola vez por tipo de contenido). */
    suspend fun ensureGenres() {
        if (withContext(Dispatchers.IO) { genreDao.count(MediaType.MOVIE.name) } == 0) {
            when (val result = apiCall { tmdb.movieGenres() }) {
                is ApiResult.Success -> {
                    val genres = result.value.genres.orEmpty().mapNotNull { g ->
                        g.id?.let { id -> g.name?.let { name -> GenreEntity(id, MediaType.MOVIE.name, name) } }
                    }
                    if (genres.isNotEmpty()) genreDao.upsertGenres(genres)
                }
                else -> Unit
            }
        }
        val tvTypes = listOf(MediaType.SERIES, MediaType.ANIME)
        val needsTvGenres = withContext(Dispatchers.IO) {
            genreDao.count(MediaType.SERIES.name) == 0 || genreDao.count(MediaType.ANIME.name) == 0
        }
        if (needsTvGenres) {
            when (val result = apiCall { tmdb.tvGenres() }) {
                is ApiResult.Success -> {
                    val pairs = result.value.genres.orEmpty().mapNotNull { g ->
                        val id = g.id ?: return@mapNotNull null
                        val name = g.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        id to name
                    }
                    if (pairs.isNotEmpty()) {
                        genreDao.upsertGenres(
                            pairs.flatMap { (id, name) -> tvTypes.map { GenreEntity(id, it.name, name) } }
                        )
                    }
                }
                else -> Unit
            }
        }
    }

    companion object {
        private const val MAX_EPISODE_PAGES = 5
        private const val ENRICH_TTL_MS = 7L * 24 * 60 * 60 * 1000
    }
}
