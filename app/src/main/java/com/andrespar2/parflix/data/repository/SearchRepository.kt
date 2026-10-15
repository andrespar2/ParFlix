package com.andrespar2.parflix.data.repository

import com.andrespar2.parflix.core.network.ApiResult
import com.andrespar2.parflix.core.network.apiCall
import com.andrespar2.parflix.core.util.TextNormalization
import com.andrespar2.parflix.core.util.TimeUtils
import com.andrespar2.parflix.data.local.ParFlixDatabase
import com.andrespar2.parflix.data.local.entity.CatalogItemEntity
import com.andrespar2.parflix.data.local.entity.SearchQueryEntity
import com.andrespar2.parflix.data.local.model.StoredRecommendation
import com.andrespar2.parflix.data.remote.TmdbApi
import com.andrespar2.parflix.domain.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Resultado de búsqueda local.
 *
 * @param items coincidencias ordenadas por relevancia.
 * @param idMatch coincidencia exacta por IMDb ID o TMDB ID, si la hubo.
 * @param usedFuzzy `true` si hubo que recurrir al respaldo tolerante a errores.
 * @param remoteSuggestions coincidencias de TMDB que no están en el catálogo local
 *        (solo se piden si no hubo resultados locales; su reproducción depende de Vimeus).
 */
data class SearchResult(
    val items: List<CatalogItemEntity> = emptyList(),
    val idMatch: CatalogItemEntity? = null,
    val usedFuzzy: Boolean = false,
    val remoteSuggestions: List<StoredRecommendation> = emptyList(),
    val error: String? = null,
)

/**
 * Búsqueda inteligente ejecutada **en el dispositivo**:
 * índices FTS4, normalización sin tildes, búsqueda por prefijos, coincidencias
 * exactas por IMDb/TMDB y respaldo por similitud para errores ortográficos leves.
 * La búsqueda remota en TMDB es un complemento opcional y limitado.
 */
class SearchRepository(
    private val database: ParFlixDatabase,
    private val tmdb: TmdbApi,
) {

    private val ftsDao = database.catalogFtsDao()
    private val catalogDao = database.catalogDao()
    private val queryDao = database.searchQueryDao()

    fun observeRecentQueries(limit: Int = 10): Flow<List<SearchQueryEntity>> =
        queryDao.observeRecent(limit).flowOn(Dispatchers.IO)

    suspend fun rememberQuery(raw: String) = withContext(Dispatchers.IO) {
        val normalized = TextNormalization.normalize(raw)
        if (normalized.length < 2) return@withContext
        queryDao.upsert(SearchQueryEntity(query = raw.trim(), normalized = normalized, lastUsedAt = TimeUtils.nowMillis()))
        queryDao.trim(15)
    }

    suspend fun forgetQuery(query: String) = withContext(Dispatchers.IO) { queryDao.delete(query) }

    suspend fun clearRecentQueries() = withContext(Dispatchers.IO) { queryDao.clear() }

    /**
     * Búsqueda principal. Nunca lanza excepciones: ante cualquier problema
     * devuelve un [SearchResult] con el error descrito.
     *
     * @param allowRemote activa el complemento de TMDB cuando no hay resultados locales.
     */
    suspend fun search(
        query: String,
        mediaType: MediaType? = null,
        limit: Int = 60,
        allowRemote: Boolean = true,
    ): SearchResult = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext SearchResult()

        val normalized = TextNormalization.normalize(trimmed)
        val typeName = mediaType?.name
        val results = LinkedHashMap<String, CatalogItemEntity>()
        var usedFuzzy = false

        // 1) Coincidencias exactas por identificador (IMDb o TMDB).
        var idMatch: CatalogItemEntity? = null
        val imdbCandidate = Regex("tt\\d{5,}").find(trimmed)?.value
        if (imdbCandidate != null) {
            idMatch = catalogDao.byImdbId(imdbCandidate, 1).firstOrNull()
        } else if (normalized.matches(Regex("\\d{1,9}"))) {
            idMatch = catalogDao.byTmdbId(normalized.toInt(), 1).firstOrNull()
        }
        idMatch?.let { results[keyOf(it)] = it }

        // 2) Índice FTS4 (prefijos por término, sin tildes ni mayúsculas).
        val ftsQuery = TextNormalization.toFtsQuery(trimmed)
        val yearFilter = Regex("\\b(19|20)\\d{2}\\b").find(trimmed)?.value?.toIntOrNull()
        if (ftsQuery.isNotBlank()) {
            val ftsMatches = runCatching { ftsDao.search(ftsQuery, typeName, limit) }.getOrDefault(emptyList())
            ftsMatches.forEach { results.putIfAbsent(keyOf(it), it) }
        }

        // 3) Respaldo tolerante a errores ortográficos leves.
        if (results.size < MIN_RESULTS_BEFORE_FUZZY) {
            val fuzzy = fuzzySearch(normalized, typeName, limit - results.size)
            if (fuzzy.isNotEmpty()) {
                usedFuzzy = true
                fuzzy.forEach { results.putIfAbsent(keyOf(it), it) }
            }
        }

        // 4) Filtro por año si la consulta lo incluye (además del texto).
        var ordered = results.values.toList()
        if (yearFilter != null) {
            val withYear = ordered.filter { it.year == yearFilter }
            if (withYear.isNotEmpty()) ordered = withYear
        }

        val sorted = ordered
            .sortedWith(
                compareByDescending<CatalogItemEntity> { scoreOf(it, normalized) }
                    .thenByDescending { it.popularity ?: 0.0 }
                    .thenByDescending { it.voteCount ?: 0 }
            )
            .take(limit)

        // 5) Complemento remoto opcional, solo si no hubo nada local.
        var remote: List<StoredRecommendation> = emptyList()
        if (allowRemote && sorted.isEmpty() && trimmed.length >= 3) {
            remote = remoteSearch(trimmed)
        }

        SearchResult(
            items = sorted,
            idMatch = idMatch,
            usedFuzzy = usedFuzzy,
            remoteSuggestions = remote,
        )
    }

    /** Sugerencias para el campo de búsqueda (títulos locales por prefijo). */
    suspend fun suggestions(prefix: String, limit: Int = 8): List<CatalogItemEntity> =
        withContext(Dispatchers.IO) {
            val ftsQuery = TextNormalization.toFtsQuery(prefix)
            if (ftsQuery.isBlank()) return@withContext emptyList()
            runCatching { ftsDao.search(ftsQuery, null, limit) }.getOrDefault(emptyList())
        }

    /**
     * Respaldo por similitud sobre el índice local. La comparación se hace en
     * memoria sobre un máximo acotado de candidatos; solo se ejecuta cuando la
     * búsqueda exacta no encontró casi nada.
     */
    private suspend fun fuzzySearch(
        normalizedQuery: String,
        typeName: String?,
        limit: Int,
    ): List<CatalogItemEntity> {
        if (limit <= 0 || normalizedQuery.isBlank()) return emptyList()
        val candidates = ftsDao.fuzzyCandidates(MAX_FUZZY_CANDIDATES)
        if (candidates.isEmpty()) return emptyList()

        val scored = candidates.mapNotNull { candidate ->
            val score = bestWordSimilarity(normalizedQuery, candidate.names)
            if (score >= FUZZY_THRESHOLD) candidate.rowId to score else null
        }

        val ids = scored.sortedByDescending { it.second }.take(limit).map { it.first }
        if (ids.isEmpty()) return emptyList()
        val items = catalogDao.byIds(ids)
        return if (typeName == null) items else items.filter { it.mediaType == typeName }
    }

    /** Similitud máxima entre la consulta y cualquiera de las palabras del título. */
    private fun bestWordSimilarity(normalizedQuery: String, names: String): Double {
        if (names.isBlank()) return 0.0
        if (names.contains(normalizedQuery)) return 1.0
        val words = names.split(' ')
        var best = 0.0
        for (word in words) {
            if (word.length < 3) continue
            if (word.startsWith(normalizedQuery) || normalizedQuery.startsWith(word)) {
                return 0.98
            }
            val similarity = TextNormalization.similarity(normalizedQuery, word)
            if (similarity > best) best = similarity
        }
        return best
    }

    /** Búsqueda en TMDB para títulos que aún no están en el catálogo local. */
    private suspend fun remoteSearch(query: String): List<StoredRecommendation> {
        val result = apiCall(maxAttempts = 2) { tmdb.searchMulti(query) }
        if (result !is ApiResult.Success) return emptyList()
        return result.value.results.orEmpty().take(12).mapNotNull { dto ->
            val id = dto.id ?: return@mapNotNull null
            val type = MediaType.fromString(dto.mediaType) ?: return@mapNotNull null
            if (type == MediaType.ANIME) return@mapNotNull null
            val title = (dto.title ?: dto.name)?.trim().orEmpty()
            if (title.isEmpty()) return@mapNotNull null
            StoredRecommendation(
                tmdbId = id,
                mediaType = type.name,
                title = title,
                posterPath = dto.posterPath,
                year = TimeUtils.yearOf(dto.releaseDate ?: dto.firstAirDate),
                voteAverage = dto.voteAverage,
            )
        }
    }

    /** Puntuación de relevancia local (coincidencia exacta > prefijo > resto). */
    private fun scoreOf(item: CatalogItemEntity, normalizedQuery: String): Double {
        val title = item.titleNormalized.ifBlank { TextNormalization.normalize(item.title) }
        return when {
            title == normalizedQuery -> 100.0
            title.startsWith(normalizedQuery) -> 80.0
            title.contains(" $normalizedQuery") -> 60.0
            title.contains(normalizedQuery) -> 40.0
            else -> 10.0
        }
    }

    private fun keyOf(item: CatalogItemEntity): String = "${item.mediaType}:${item.tmdbId}"

    private companion object {
        const val MIN_RESULTS_BEFORE_FUZZY = 5
        const val MAX_FUZZY_CANDIDATES = 30_000
        const val FUZZY_THRESHOLD = 0.72
    }
}
