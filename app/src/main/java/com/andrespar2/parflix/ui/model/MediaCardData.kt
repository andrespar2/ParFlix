package com.andrespar2.parflix.ui.model

import com.andrespar2.parflix.data.local.entity.CatalogItemEntity
import com.andrespar2.parflix.data.local.entity.FavoriteEntity
import com.andrespar2.parflix.data.local.entity.WatchHistoryEntity
import com.andrespar2.parflix.domain.MediaType

/**
 * Modelo ligero de tarjeta usado por las filas y cuadrículas.
 * Desacopla la interfaz de las entidades concretas (catálogo, favoritos,
 * historial), que muestran la misma información con distinto origen.
 */
data class MediaCardData(
    val tmdbId: Int,
    val mediaType: MediaType?,
    val title: String,
    val posterPath: String?,
    val year: Int?,
    val rating: Double?,
    /** Subtítulo opcional (p. ej. "T2 E5" en Continuar viendo). */
    val subtitle: String? = null,
    /** Progreso conocido [0,1] si el reproductor lo expuso; nunca estimado. */
    val progress: Float? = null,
)

fun CatalogItemEntity.toCard(): MediaCardData = MediaCardData(
    tmdbId = tmdbId,
    mediaType = type,
    title = title,
    posterPath = posterPath,
    year = year,
    rating = voteAverage,
)

fun FavoriteEntity.toCard(): MediaCardData = MediaCardData(
    tmdbId = tmdbId,
    mediaType = MediaType.fromString(mediaType),
    title = title,
    posterPath = posterPath,
    year = year,
    rating = voteAverage,
)

fun WatchHistoryEntity.toCard(): MediaCardData = MediaCardData(
    tmdbId = tmdbId,
    mediaType = MediaType.fromString(mediaType),
    title = title,
    posterPath = posterPath,
    year = year,
    rating = null,
    subtitle = episodeLabel(),
    progress = progressFraction(),
)

private fun WatchHistoryEntity.episodeLabel(): String? {
    val season = lastSeason ?: return null
    val episode = lastEpisode ?: return null
    return "T$season E$episode"
}

/** Solo se usa si hay posición y duración autorizadas por el reproductor. */
private fun WatchHistoryEntity.progressFraction(): Float? {
    val position = lastPositionMs ?: return null
    val duration = durationMs ?: return null
    if (duration <= 0L) return null
    return (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
}
