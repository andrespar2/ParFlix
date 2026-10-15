package com.andrespar2.parflix.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Respuesta genérica de Vimeus: `{ "error": false, "message": "Success", "data": {...} }`.
 * Todos los campos son opcionales porque el proveedor no garantiza su presencia.
 */
@JsonClass(generateAdapter = true)
data class VimeusEnvelope<T>(
    val error: Boolean? = null,
    val message: String? = null,
    val data: T? = null,
)

/** Bloque de datos de un listado: `{ "pages": 81, "result": [...] }`. */
@JsonClass(generateAdapter = true)
data class VimeusListingData<T>(
    val pages: Int? = null,
    val result: List<T>? = null,
)

/**
 * Registro de los listados y de los episodios. Los listados de películas
 * incluyen `imdb_id`, `tmdb_id`, `title`, `poster`, `backdrop`, `embed_url`,
 * `download_url` y `quality`; el endpoint de episodios añade `season`,
 * `episode`, `show_title` y `parent_type`. Cualquiera puede faltar.
 */
@JsonClass(generateAdapter = true)
data class VimeusItemDto(
    @Json(name = "imdb_id") val imdbId: String? = null,
    @Json(name = "tmdb_id") val tmdbId: Int? = null,
    @Json(name = "content_type") val contentType: String? = null,
    val title: String? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    @Json(name = "embed_url") val embedUrl: String? = null,
    @Json(name = "download_url") val downloadUrl: String? = null,
    val quality: String? = null,
    @Json(name = "synced_at") val syncedAt: String? = null,
    @Json(name = "total_seasons") val totalSeasons: Int? = null,
    @Json(name = "total_episodes") val totalEpisodes: Int? = null,
    // Campos presentes en el endpoint de episodios (llegan como texto).
    val season: String? = null,
    val episode: String? = null,
    @Json(name = "show_title") val showTitle: String? = null,
    @Json(name = "parent_type") val parentType: String? = null,
)
