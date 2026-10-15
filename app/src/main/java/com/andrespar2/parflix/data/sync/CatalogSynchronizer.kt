package com.andrespar2.parflix.data.sync

import com.andrespar2.parflix.core.network.ApiResult
import com.andrespar2.parflix.core.network.NetworkMonitor
import com.andrespar2.parflix.core.network.apiCall
import com.andrespar2.parflix.core.util.TimeUtils
import com.andrespar2.parflix.data.local.CatalogWriter
import com.andrespar2.parflix.data.local.ParFlixDatabase
import com.andrespar2.parflix.data.local.entity.CatalogItemEntity
import com.andrespar2.parflix.data.local.entity.SyncPageEntity
import com.andrespar2.parflix.data.local.entity.SyncStateEntity
import com.andrespar2.parflix.data.preferences.UserPreferences
import com.andrespar2.parflix.data.remote.VimeusApi
import com.andrespar2.parflix.data.remote.dto.VimeusItemDto
import com.andrespar2.parflix.data.remote.dto.VimeusListingData
import com.andrespar2.parflix.data.remote.mapper.VimeusMappers
import com.andrespar2.parflix.data.repository.DetailsRepository
import com.andrespar2.parflix.domain.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlin.math.max

enum class SyncPhase { IDLE, CATALOG, ENRICH, COMPLETE, FAILED }

data class SyncProgress(
    val running: Boolean = false,
    val phase: SyncPhase = SyncPhase.IDLE,
    val mediaType: MediaType? = null,
    val page: Int = 0,
    val totalPages: Int = 0,
    val added: Int = 0,
    val updated: Int = 0,
    val removed: Int = 0,
    val enriched: Int = 0,
    val error: String? = null,
    val startedAt: Long = 0,
    val finishedAt: Long = 0,
) {
    /** Progreso aproximado [0,1] del barrido del tipo en curso. */
    val pageFraction: Float
        get() = if (totalPages <= 0) 0f else (page.toFloat() / totalPages).coerceIn(0f, 1f)
}

data class SyncOutcome(
    val success: Boolean,
    val added: Int = 0,
    val updated: Int = 0,
    val removed: Int = 0,
    val enriched: Int = 0,
    val completedFullScan: Boolean = false,
    val error: String? = null,
)

/**
 * Motor de sincronización del catálogo. Toda la lógica se ejecuta en el
 * dispositivo; no existe backend central.
 *
 * Estrategia:
 * - **Incremental**: en cada apertura se recorren unas pocas páginas por tipo,
 *   continuando desde un checkpoint (barrido rodante). Al llegar al final se
 *   vuelve a la página 1, de modo que el catálogo completo se cubre con el
 *   tiempo sin descargarlo entero cada vez.
 * - **Huellas por página**: si el contenido de una página no cambió, no se
 *   reescribe nada; solo se marca como visto.
 * - **Reconciliación completa**: barrido íntegro que además elimina lo que ya
 *   no existe. Solo se elimina si el barrido terminó sin errores, para no
 *   borrar datos válidos por un fallo temporal.
 * - **Presupuesto de tiempo**: una sincronización larga se detiene de forma
 *   ordenada y continúa en la siguiente apertura (checkpoints), evitando
 *   bloquear la navegación o castigar la batería.
 * - **Enriquecimiento progresivo** con TMDB tras cada sincronización.
 */
class CatalogSynchronizer(
    private val database: ParFlixDatabase,
    private val vimeus: VimeusApi,
    private val detailsRepository: DetailsRepository,
    private val networkMonitor: NetworkMonitor,
    private val preferences: UserPreferences,
) {

    private val catalogDao = database.catalogDao()
    private val syncDao = database.syncDao()
    private val writer = CatalogWriter(database)

    /** Evita sincronizaciones simultáneas (apertura + WorkManager + acción manual). */
    private val mutex = Mutex()

    private val _progress = MutableStateFlow(SyncProgress())
    val progress: StateFlow<SyncProgress> = _progress.asStateFlow()

    /** Verificación ligera al abrir la app o volver al primer plano. */
    suspend fun syncOnOpen(
        pagesPerType: Int = DEFAULT_PAGES_ON_OPEN,
        enrichLimit: Int = 20,
        budgetMs: Long = DEFAULT_BUDGET_MS,
    ): SyncOutcome = run(pagesPerType, enrichLimit, fullReconcile = false, budgetMs = budgetMs)

    /** Barrido íntegro con reconciliación (elimina lo desaparecido del proveedor). */
    suspend fun fullReconcile(
        enrichLimit: Int = 40,
        budgetMs: Long = LONG_BUDGET_MS,
    ): SyncOutcome = run(Int.MAX_VALUE, enrichLimit, fullReconcile = true, budgetMs = budgetMs)

    /** Primera ejecución: catálogo completo por tramos, sin bloquear la interfaz. */
    suspend fun initialProgressiveSync(
        enrichLimit: Int = 30,
        budgetMs: Long = FIRST_RUN_BUDGET_MS,
    ): SyncOutcome = run(Int.MAX_VALUE, enrichLimit, fullReconcile = true, budgetMs = budgetMs)

    /**
     * Indica si toca una reconciliación completa (barrido íntegro + borrado de
     * lo desaparecido). Se compara el barrido íntegro más antiguo de los tres
     * tipos; si alguno nunca se recorrió por completo, se considera vencido.
     */
    suspend fun isFullScanDue(intervalDays: Int): Boolean = withContext(Dispatchers.IO) {
        val now = TimeUtils.nowMillis()
        val threshold = now - intervalDays.coerceAtLeast(1) * DAY_MS
        MediaType.entries.any { type ->
            val state = syncDao.state(type.name)
            val lastFull = state?.lastFullReconcileAt ?: 0L
            lastFull <= threshold
        }
    }

    /** Indica si la primera sincronización sigue pendiente de terminar. */
    suspend fun isCatalogEmpty(): Boolean = withContext(Dispatchers.IO) {
        catalogDao.count(MediaType.MOVIE.name) == 0 &&
            catalogDao.count(MediaType.SERIES.name) == 0 &&
            catalogDao.count(MediaType.ANIME.name) == 0
    }

    private suspend fun run(
        pagesPerType: Int,
        enrichLimit: Int,
        fullReconcile: Boolean,
        budgetMs: Long,
    ): SyncOutcome {
        if (!networkMonitor.currentlyOnline()) {
            _progress.value = SyncProgress(
                phase = SyncPhase.FAILED,
                error = "Sin conexión a internet",
                finishedAt = TimeUtils.nowMillis(),
            )
            return SyncOutcome(success = false, error = "Sin conexión a internet")
        }

        return mutex.withLock {
            if (_progress.value.running) {
                return@withLock SyncOutcome(success = false, error = "Sincronización ya en curso")
            }
            val startedAt = TimeUtils.nowMillis()
            _progress.value = SyncProgress(running = true, phase = SyncPhase.CATALOG, startedAt = startedAt)

            var added = 0
            var updated = 0
            var removed = 0
            var failure: String? = null
            var allTypesCompleted = true

            detailsRepository.ensureGenres()

            for (type in MediaType.entries) {
                val result = syncType(type, pagesPerType, fullReconcile, startedAt, budgetMs)
                added += result.added
                updated += result.updated
                removed += result.removed
                if (result.error != null) {
                    failure = result.error
                    allTypesCompleted = false
                    break
                }
                if (!result.completed) allTypesCompleted = false
            }

            var enriched = 0
            if (failure == null && allTypesCompleted) {
                _progress.value = _progress.value.copy(phase = SyncPhase.ENRICH)
                enriched = detailsRepository.enrichPending(enrichLimit)
                preferences.setLastCatalogSyncAt(TimeUtils.nowMillis())
            } else if (failure == null) {
                // Barrido parcial: se enriquece poco para no competir con la navegación.
                enriched = detailsRepository.enrichPending(enrichLimit / 2)
            }

            val finishedAt = TimeUtils.nowMillis()
            if (failure == null) {
                _progress.value = _progress.value.copy(
                    running = false,
                    phase = SyncPhase.COMPLETE,
                    added = added,
                    updated = updated,
                    removed = removed,
                    enriched = enriched,
                    finishedAt = finishedAt,
                )
            } else {
                _progress.value = _progress.value.copy(
                    running = false,
                    phase = SyncPhase.FAILED,
                    added = added,
                    updated = updated,
                    removed = removed,
                    error = failure,
                    finishedAt = finishedAt,
                )
            }

            SyncOutcome(
                success = failure == null,
                added = added,
                updated = updated,
                removed = removed,
                enriched = enriched,
                completedFullScan = allTypesCompleted,
                error = failure,
            )
        }
    }

    private data class TypeResult(
        val added: Int,
        val updated: Int,
        val removed: Int,
        val completed: Boolean,
        val error: String?,
    )

    private suspend fun syncType(
        type: MediaType,
        pagesToScan: Int,
        fullReconcile: Boolean,
        startedAt: Long,
        budgetMs: Long,
    ): TypeResult {
        val stored = withContext(Dispatchers.IO) { syncDao.state(type.name) } ?: SyncStateEntity(key = type.name)
        val generation = if (fullReconcile) stored.generation + 1 else max(stored.generation, 1L)
        val now = TimeUtils.nowMillis()

        var page = if (fullReconcile) 1 else stored.nextPage.coerceAtLeast(1)
        var totalPages = stored.totalPages
        var scanned = 0
        var added = 0
        var updated = 0
        var lastError: String? = null
        var completed = false
        var outOfBudget = false

        while (scanned < pagesToScan) {
            if (TimeUtils.nowMillis() - startedAt > budgetMs) {
                outOfBudget = true
                break
            }

            when (val result = apiCall(maxAttempts = 3) { fetchPage(type, page) }) {
                is ApiResult.Success -> {
                    val data: VimeusListingData<VimeusItemDto> = result.value
                    val rawItems = data.result.orEmpty()
                    totalPages = data.pages?.coerceAtLeast(1) ?: totalPages

                    if (rawItems.isEmpty() && page == 1) {
                        // Tipo sin contenido (o endpoint vacío): nada que sincronizar.
                        completed = true
                        break
                    }

                    val items = rawItems.mapNotNull { dto ->
                        VimeusMappers.toCatalogItem(dto, type, now = now, generation = generation)
                    }

                    val fingerprint = fingerprintOf(items)
                    val pageKey = "${type.name}:$page"
                    val storedPage = withContext(Dispatchers.IO) { syncDao.page(pageKey) }

                    if (storedPage?.fingerprint != fingerprint) {
                        val counts = writer.upsertItems(items)
                        added += counts.inserted
                        updated += counts.updated
                        withContext(Dispatchers.IO) {
                            syncDao.putPages(
                                listOf(
                                    SyncPageEntity(
                                        key = pageKey,
                                        fingerprint = fingerprint,
                                        itemCount = items.size,
                                        updatedAt = now,
                                    )
                                )
                            )
                        }
                    }

                    // Marcar como visto siempre: base de la reconciliación.
                    if (items.isNotEmpty()) {
                        withContext(Dispatchers.IO) {
                            catalogDao.markSeen(type.name, items.map { it.tmdbId }, generation)
                        }
                    }

                    scanned++
                    _progress.value = _progress.value.copy(
                        mediaType = type,
                        page = page,
                        totalPages = totalPages,
                        added = added,
                        updated = updated,
                    )

                    if (totalPages in 1..page) {
                        completed = true
                        page = 1
                        break
                    }
                    page++
                    // Pausa breve entre páginas: mantiene fluida la interfaz y es amable con el proveedor.
                    delay(INTER_PAGE_DELAY_MS)
                }
                is ApiResult.HttpError -> {
                    lastError = "El proveedor respondió ${result.code}" + (result.message?.let { ": $it" } ?: "")
                    break
                }
                is ApiResult.NetworkError -> {
                    lastError = result.cause
                    break
                }
                is ApiResult.DataError -> {
                    lastError = "Respuesta inesperada del proveedor: ${result.cause}"
                    break
                }
            }
        }

        val finishedAt = TimeUtils.nowMillis()
        val mayDeleteStale = fullReconcile && completed && lastError == null
        val removedCount = if (mayDeleteStale) {
            withContext(Dispatchers.IO) { writer.removeStale(type.name, generation) }
        } else {
            0
        }

        withContext(Dispatchers.IO) {
            syncDao.putState(
                SyncStateEntity(
                    key = type.name,
                    lastSyncAt = if (lastError == null) finishedAt else stored.lastSyncAt,
                    lastFullReconcileAt = if (mayDeleteStale) finishedAt else stored.lastFullReconcileAt,
                    nextPage = page,
                    totalPages = totalPages,
                    generation = generation,
                    lastError = lastError ?: if (outOfBudget) "barrido parcial (continúa en la próxima)" else null,
                )
            )
        }

        return TypeResult(
            added = added,
            updated = updated,
            removed = removedCount,
            completed = completed,
            error = lastError,
        )
    }

    private suspend fun fetchPage(type: MediaType, page: Int): VimeusListingData<VimeusItemDto> {
        val envelope = when (type) {
            MediaType.MOVIE -> vimeus.movies(page)
            MediaType.SERIES -> vimeus.series(page)
            MediaType.ANIME -> vimeus.animes(page)
        }
        if (envelope.error == true) {
            throw IllegalStateException(envelope.message ?: "El proveedor devolvió un error")
        }
        return envelope.data ?: VimeusListingData()
    }

    /** Huella estable del contenido de una página (detecta cambios sin reescribir). */
    private fun fingerprintOf(items: List<CatalogItemEntity>): String {
        if (items.isEmpty()) return "empty"
        val digest = MessageDigest.getInstance("SHA-1")
        items.forEach { item ->
            val line = "${item.tmdbId}|${item.imdbId.orEmpty()}|${item.title}|${item.posterPath.orEmpty()}|${item.embedUrl.orEmpty()}"
            digest.update(line.toByteArray(Charsets.UTF_8))
            digest.update(0)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val DEFAULT_PAGES_ON_OPEN = 5
        private const val DEFAULT_BUDGET_MS = 25_000L
        private const val LONG_BUDGET_MS = 180_000L
        private const val FIRST_RUN_BUDGET_MS = 90_000L
        private const val INTER_PAGE_DELAY_MS = 120L
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
