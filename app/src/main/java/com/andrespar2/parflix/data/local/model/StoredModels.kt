package com.andrespar2.parflix.data.local.model

import com.squareup.moshi.JsonClass

/**
 * Representaciones compactas de los metadatos secundarios de TMDB.
 * Se guardan como JSON dentro de `details` para evitar decenas de tablas y
 * consultas costosas. Se escriben una vez y se leen bajo demanda.
 */
@JsonClass(generateAdapter = true)
data class StoredGenre(val id: Int, val name: String)

@JsonClass(generateAdapter = true)
data class StoredPerson(
    val id: Int?,
    val name: String,
    val role: String? = null,
    val photoPath: String? = null,
)

@JsonClass(generateAdapter = true)
data class StoredCompany(
    val id: Int?,
    val name: String,
    val logoPath: String? = null,
)

@JsonClass(generateAdapter = true)
data class StoredCountry(val code: String?, val name: String)

@JsonClass(generateAdapter = true)
data class StoredLanguage(val code: String?, val name: String)

@JsonClass(generateAdapter = true)
data class StoredVideo(
    val key: String,
    val name: String? = null,
    val site: String? = null,
    val type: String? = null,
    val language: String? = null,
    val official: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class StoredRecommendation(
    val tmdbId: Int,
    val mediaType: String,
    val title: String,
    val posterPath: String? = null,
    val year: Int? = null,
    val voteAverage: Double? = null,
)

@JsonClass(generateAdapter = true)
data class StoredSeason(
    val number: Int,
    val name: String? = null,
    val episodeCount: Int? = null,
    val posterPath: String? = null,
    val airDate: String? = null,
    val overview: String? = null,
)

@JsonClass(generateAdapter = true)
data class StoredRating(val country: String, val rating: String)
