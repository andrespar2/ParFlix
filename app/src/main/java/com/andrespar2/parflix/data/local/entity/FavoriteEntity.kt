package com.andrespar2.parflix.data.local.entity

import androidx.room.Entity

/**
 * Favorito / "Mi lista". Guarda una copia ligera del título para poder mostrar
 * la lista sin conexión, aunque el catálogo aún no se haya sincronizado.
 */
@Entity(tableName = "favorites", primaryKeys = ["tmdbId", "mediaType"])
data class FavoriteEntity(
    val tmdbId: Int,
    val mediaType: String,
    val title: String,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val year: Int? = null,
    val voteAverage: Double? = null,
    val addedAt: Long,
)
