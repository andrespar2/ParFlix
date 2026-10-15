package com.andrespar2.parflix.core.util

/**
 * Construcción de URLs de imágenes de TMDB.
 * Cualquier uso de imágenes exige la atribución indicada en "Acerca de".
 */
object ImageUrls {

    private const val BASE = "https://image.tmdb.org/t/p"

    const val POSTER_SMALL = "w342"
    const val POSTER_MEDIUM = "w500"
    const val BACKDROP = "w780"
    const val BACKDROP_LARGE = "w1280"
    const val PROFILE = "w185"
    const val STILL = "w300"
    const val ORIGINAL = "original"

    fun poster(path: String?, size: String = POSTER_MEDIUM): String? = build(path, size)

    fun backdrop(path: String?, size: String = BACKDROP): String? = build(path, size)

    fun profile(path: String?, size: String = PROFILE): String? = build(path, size)

    fun still(path: String?, size: String = STILL): String? = build(path, size)

    private fun build(path: String?, size: String): String? {
        if (path.isNullOrBlank()) return null
        val clean = if (path.startsWith("/")) path else "/$path"
        // Algunos registros de Vimeus ya traen la URL completa.
        if (clean.startsWith("http")) return clean
        return "$BASE/$size$clean"
    }
}
