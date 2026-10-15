package com.andrespar2.parflix.data.remote.mapper

import com.andrespar2.parflix.core.util.TextNormalization
import com.andrespar2.parflix.core.util.TimeUtils
import com.andrespar2.parflix.data.local.entity.CatalogFtsEntity
import com.andrespar2.parflix.data.local.entity.CatalogItemEntity
import com.andrespar2.parflix.data.local.entity.EpisodeEntity
import com.andrespar2.parflix.data.remote.dto.VimeusItemDto
import com.andrespar2.parflix.domain.MediaType
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Conversión de los registros de Vimeus al modelo local.
 *
 * Robustez deliberada: el proveedor no garantiza la presencia de campos, así
 * que los registros incompletos se descartan en lugar de provocar fallos.
 */
object VimeusMappers {

    private const val ALLOWED_HOST = "vimeus.com"

    /** Registro de listado → título del catálogo. Devuelve null si falta lo esencial. */
    fun toCatalogItem(
        dto: VimeusItemDto,
        mediaType: MediaType,
        now: Long,
        generation: Long,
    ): CatalogItemEntity? {
        val tmdbId = dto.tmdbId ?: return null
        val title = dto.title?.trim().orEmpty()
        if (title.isEmpty()) return null

        return CatalogItemEntity(
            tmdbId = tmdbId,
            mediaType = mediaType.name,
            imdbId = dto.imdbId?.trim()?.takeIf { it.isNotEmpty() },
            title = title,
            titleNormalized = TextNormalization.normalize(title),
            posterPath = dto.poster?.trim()?.takeIf { it.isNotEmpty() },
            backdropPath = dto.backdrop?.trim()?.takeIf { it.isNotEmpty() },
            quality = dto.quality?.trim()?.takeIf { it.isNotEmpty() },
            embedUrl = sanitizeEmbedUrl(dto.embedUrl, mediaType),
            syncedAt = TimeUtils.parseToEpochMillis(dto.syncedAt),
            addedAt = now,
            lastSeenAt = generation,
            totalSeasons = dto.totalSeasons,
            totalEpisodes = dto.totalEpisodes,
        )
    }

    /** Registro de episodio → entidad local (disponibilidad real en Vimeus). */
    fun toEpisode(
        dto: VimeusItemDto,
        mediaType: MediaType,
        now: Long,
    ): EpisodeEntity? {
        val tmdbId = dto.tmdbId ?: return null
        val season = dto.season?.trim()?.toIntOrNull() ?: return null
        val episode = dto.episode?.trim()?.toIntOrNull() ?: return null
        return EpisodeEntity(
            tmdbId = tmdbId,
            mediaType = mediaType.name,
            season = season,
            episode = episode,
            title = dto.title?.trim()?.takeIf { it.isNotEmpty() },
            stillPath = dto.poster?.trim()?.takeIf { it.isNotEmpty() },
            embedUrl = sanitizeEmbedUrl(dto.embedUrl, mediaType),
            available = true,
            fromVimeus = true,
            fromTmdb = false,
            fetchedAt = now,
        )
    }

    /** Fila FTS con todos los nombres conocidos del título. */
    fun toFtsRow(
        item: CatalogItemEntity,
        originalTitle: String?,
        altTitles: List<String>,
        year: Int?,
    ): CatalogFtsEntity {
        val altNormalized = altTitles
            .map { TextNormalization.normalize(it) }
            .filter { it.isNotBlank() }
            .distinct()
        val originalNormalized = TextNormalization.normalize(originalTitle)
        val names = buildSet {
            add(item.titleNormalized)
            if (originalNormalized.isNotBlank()) add(originalNormalized)
            addAll(altNormalized)
        }.filter { it.isNotBlank() }.joinToString(" ")

        return CatalogFtsEntity(
            rowId = item.id,
            title = item.titleNormalized,
            originalTitle = originalNormalized,
            altTitles = altNormalized.joinToString(" "),
            names = names,
            imdbId = item.imdbId.orEmpty(),
            tmdbId = item.tmdbId.toString(),
            year = year?.toString().orEmpty(),
        )
    }

    /**
     * Solo se aceptan URLs de reproducción del dominio autorizado y por HTTPS.
     * Nunca se inventan ni se alteran orígenes.
     */
    fun sanitizeEmbedUrl(raw: String?, mediaType: MediaType): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        val uri = runCatching { value.toHttpUrl() }.getOrNull() ?: return null
        if (uri.scheme != "https") return null
        val host = uri.host.lowercase()
        if (host != ALLOWED_HOST && !host.endsWith(".$ALLOWED_HOST")) return null
        val path = uri.pathSegments
        // El segmento del tipo debe corresponder (e/movie, e/serie, e/anime).
        if (path.size >= 2 && path[0] == "e" && path[1] != mediaType.vimeusSegment) return null
        return uri.toString()
    }
}
