package com.andrespar2.parflix.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Historial de visualización y estado para "Continuar viendo".
 *
 * `lastPositionMs` solo se rellena si el reproductor expone una forma
 * autorizada de conocer la posición (no se estima ni se inventa).
 */
@Entity(
    tableName = "watch_history",
    primaryKeys = ["tmdbId", "mediaType"],
    indices = [Index(value = ["lastWatchedAt"])],
)
data class WatchHistoryEntity(
    val tmdbId: Int,
    val mediaType: String,
    val title: String,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val year: Int? = null,
    val lastSeason: Int? = null,
    val lastEpisode: Int? = null,
    val lastEpisodeTitle: String? = null,
    val lastPositionMs: Long? = null,
    val durationMs: Long? = null,
    val watchCount: Int = 1,
    val lastWatchedAt: Long,
)
