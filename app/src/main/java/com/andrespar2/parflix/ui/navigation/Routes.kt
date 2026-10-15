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

    fun catalog(mediaType: String): String = "catalog/$mediaType"

    fun catalogPattern(): String = "catalog/{$ARG_MEDIA_TYPE}"

    fun details(mediaType: String, tmdbId: Int): String = "details/$mediaType/$tmdbId"

    fun detailsPattern(): String = "details/{$ARG_MEDIA_TYPE}/{$ARG_TMDB_ID}"

    fun player(mediaType: String, tmdbId: Int, season: Int, episode: Int): String =
        "player/$mediaType/$tmdbId/$season/$episode"

    fun playerPattern(): String =
        "player/{$ARG_MEDIA_TYPE}/{$ARG_TMDB_ID}/{$ARG_SEASON}/{$ARG_EPISODE}"
}
