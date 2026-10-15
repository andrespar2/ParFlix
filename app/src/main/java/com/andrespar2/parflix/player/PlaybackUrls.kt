package com.andrespar2.parflix.player

import com.andrespar2.parflix.domain.MediaType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Construcción de las URLs de reproducción de Vimeus.
 *
 * Se usan los endpoints oficiales:
 * - `/e/movie?tmdb=ID&view_key=...`
 * - `/e/serie?tmdb=ID&se=N&ep=N&view_key=...`
 * - `/e/anime?tmdb=ID&se=N&ep=N&view_key=...`
 *
 * Si el listado ya proporciona una `embed_url` válida del dominio autorizado,
 * se usa tal cual. Nunca se falsifica el origen ni se alteran parámetros de
 * autenticación.
 */
object PlaybackUrls {

    private const val HOST = "vimeus.com"

    /**
     * @param storedEmbedUrl `embed_url` guardado (de la ficha o del episodio).
     * @return URL https de Vimeus, o null si no hay forma autorizada de construirla.
     */
    fun build(
        mediaType: MediaType,
        tmdbId: Int?,
        imdbId: String?,
        season: Int?,
        episode: Int?,
        viewKey: String,
        storedEmbedUrl: String? = null,
    ): String? {
        val valid = storedEmbedUrl?.takeIf { isValid(it, mediaType) }
        if (valid != null) return valid

        if (viewKey.isBlank()) return null

        val url = "https://$HOST/e/${mediaType.vimeusSegment}".toHttpUrlOrNull()?.newBuilder() ?: return null
        if (tmdbId == null || tmdbId <= 0) {
            val imdb = imdbId?.trim().orEmpty()
            if (imdb.isEmpty()) return null
            url.addQueryParameter("imdb", imdb)
        } else {
            url.addQueryParameter("tmdb", tmdbId.toString())
        }
        if (mediaType.isShow) {
            if (season == null || episode == null) return null
            url.addQueryParameter("se", season.toString())
            url.addQueryParameter("ep", episode.toString())
        }
        url.addQueryParameter("view_key", viewKey)
        return url.build().toString()
    }

    /** Comprueba que una URL guardada siga siendo https y del dominio autorizado. */
    fun isValid(raw: String?, mediaType: MediaType): Boolean {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return false
        val url = value.toHttpUrlOrNull() ?: return false
        if (url.scheme != "https") return false
        val host = url.host.lowercase()
        if (host != HOST && !host.endsWith(".$HOST")) return false
        val segments = url.pathSegments
        if (segments.size < 2 || segments[0] != "e") return false
        return segments[1] == mediaType.vimeusSegment
    }
}
