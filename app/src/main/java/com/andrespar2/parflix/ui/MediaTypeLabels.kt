package com.andrespar2.parflix.ui

import com.andrespar2.parflix.domain.MediaType

/** Nombre del tipo de contenido para mostrar en la interfaz. */
fun MediaType.title(): String = when (this) {
    MediaType.MOVIE -> "Películas"
    MediaType.SERIES -> "Series"
    MediaType.ANIME -> "Anime"
}

/** Nombre en minúscula, para frases. */
fun MediaType.label(): String = when (this) {
    MediaType.MOVIE -> "películas"
    MediaType.SERIES -> "series"
    MediaType.ANIME -> "anime"
}
