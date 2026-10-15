package com.andrespar2.parflix.ui.navigation

/** Rutas de navegación de ParFlix. */
object Routes {

    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val ABOUT = "about"

    const val ARG_MEDIA_TYPE = "mediaType"
    const val ARG_TMDB_ID = "tmdbId"
    const val ARG_SEASON = "season"
    const val ARG_EPISODE = "episode"

    // Catálogos. Se usan rutas literales distintas (en lugar de un único patrón
    // con argumento) para que cada pestaña sea un destino propio y la barra
    // inferior pueda alternar entre Películas, Series y Anime conservando el
    // estado de cada una.
    const val MOVIES = "catalog/movies"
    const val TV_SHOWS = "catalog/series"
    const val ANIMES = "catalog/anime"

    /** Ruta del catálogo para un tipo concreto. */
    fun catalog(mediaType: String): String = when (mediaType) {
        "SERIES" -> TV_SHOWS
        "ANIME" -> ANIMES
        else -> MOVIES
    }

    fun details(mediaType: String, tmdbId: Int): String = "details/$mediaType/$tmdbId"

    fun detailsPattern(): String = "details/{$ARG_MEDIA_TYPE}/{$ARG_TMDB_ID}"

    fun player(mediaType: String, tmdbId: Int, season: Int, episode: Int): String =
        "player/$mediaType/$tmdbId/$season/$episode"

    fun playerPattern(): String =
        "player/{$ARG_MEDIA_TYPE}/{$ARG_TMDB_ID}/{$ARG_SEASON}/{$ARG_EPISODE}"
}
