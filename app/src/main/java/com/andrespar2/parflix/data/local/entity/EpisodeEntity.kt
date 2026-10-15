package com.andrespar2.parflix.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Episodio de una serie o anime. Se fusionan dos fuentes:
 * - TMDB: metadatos (título, sinopsis, duración, imagen, fecha).
 * - Vimeus: disponibilidad real para reproducir (y su `embed_url`).
 *
 * `available = true` significa que Vimeus ofrece ese episodio.
 */
@Entity(
    tableName = "episodes",
    indices = [
        Index(value = ["tmdbId", "mediaType", "season", "episode"], unique = true),
        Index(value = ["tmdbId", "mediaType", "season"]),
        Index(value = ["fetchedAt"]),
    ],
)
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tmdbId: Int,
    val mediaType: String,
    val season: Int,
    val episode: Int,
    val title: String? = null,
    val overview: String? = null,
    val runtimeMinutes: Int? = null,
    val airDate: String? = null,
    val stillPath: String? = null,
    val voteAverage: Double? = null,
    val embedUrl: String? = null,
    val available: Boolean = false,
    val fromVimeus: Boolean = false,
    val fromTmdb: Boolean = false,
    val fetchedAt: Long,
)
