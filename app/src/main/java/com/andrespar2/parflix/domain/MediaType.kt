package com.andrespar2.parflix.domain

/** Tipo de contenido manejado por ParFlix. Coincide con las tres listas de Vimeus. */
enum class MediaType {
    MOVIE,
    SERIES,
    ANIME;

    /** `true` para series y anime (contenido por temporadas/episodios). */
    val isShow: Boolean get() = this != MOVIE

    /** Segmento usado por los endpoints de Vimeus (`/e/movie`, `/e/serie`, `/e/anime`). */
    val vimeusSegment: String
        get() = when (this) {
            MOVIE -> "movie"
            SERIES -> "serie"
            ANIME -> "anime"
        }

    /** Endpoint de listado de Vimeus correspondiente. */
    val vimeusListing: String
        get() = when (this) {
            MOVIE -> "api/listing/movies"
            SERIES -> "api/listing/series"
            ANIME -> "api/listing/animes"
        }

    /** Ruta del endpoint de TMDB (`movie` o `tv`). El anime también es TV en TMDB. */
    val tmdbPath: String get() = if (this == MOVIE) "movie" else "tv"

    companion object {
        /** Interpreta `content_type` de Vimeus o el nombre local; tolera variantes. */
        fun fromString(raw: String?): MediaType? = when (raw?.lowercase()?.trim()) {
            "movie", "movies", "pelicula", "película" -> MOVIE
            "series", "serie", "tv", "show" -> SERIES
            "anime", "animes" -> ANIME
            else -> null
        }

        /** Deduce el tipo a partir del segmento de una URL de Vimeus (`/e/...`). */
        fun fromVimeusSegment(segment: String?): MediaType? = when (segment?.lowercase()) {
            "movie" -> MOVIE
            "serie" -> SERIES
            "anime" -> ANIME
            else -> null
        }
    }
}
