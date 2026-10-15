package com.andrespar2.parflix.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Metadatos completos de TMDB para un título. Se guardan los listados
 * secundarios (reparto, productoras, vídeos, recomendaciones...) como JSON
 * para evitar decenas de tablas y consultas costosas; se leen bajo demanda.
 */
@Entity(
    tableName = "details",
    primaryKeys = ["tmdbId", "mediaType"],
    indices = [Index(value = ["mediaType"])],
)
data class DetailsEntity(
    val tmdbId: Int,
    val mediaType: String,
    val originalTitle: String? = null,
    val tagline: String? = null,
    val overview: String? = null,
    val status: String? = null,
    val releaseDate: String? = null,
    val firstAirDate: String? = null,
    val lastAirDate: String? = null,
    val runtimeMinutes: Int? = null,
    val numberOfSeasons: Int? = null,
    val numberOfEpisodes: Int? = null,
    val voteAverage: Double? = null,
    val voteCount: Int? = null,
    val popularity: Double? = null,
    val budget: Long? = null,
    val revenue: Long? = null,
    val ageRating: String? = null,
    /** JSON: `[{"id":18,"name":"Drama"}]` */
    val genresJson: String? = null,
    val countriesJson: String? = null,
    val languagesJson: String? = null,
    val companiesJson: String? = null,
    val networksJson: String? = null,
    /** JSON: reparto con personaje y foto. */
    val castJson: String? = null,
    /** JSON: dirección/creación. */
    val crewJson: String? = null,
    /** JSON: tráilers y teasers de YouTube. */
    val videosJson: String? = null,
    /** JSON: recomendaciones de TMDB. */
    val recommendationsJson: String? = null,
    /** JSON: títulos alternativos y traducciones guardados también en `alt_titles`. */
    val contentRatingsJson: String? = null,
    val externalIdsJson: String? = null,
    val seasonsJson: String? = null,
    val lastUpdated: Long,
)
