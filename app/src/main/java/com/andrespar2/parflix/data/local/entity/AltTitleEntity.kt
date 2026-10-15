package com.andrespar2.parflix.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Título alternativo/traducido obtenido de TMDB (alternative_titles + translations). */
@Entity(
    tableName = "alt_titles",
    indices = [
        Index(value = ["tmdbId", "mediaType"]),
        Index(value = ["tmdbId", "mediaType", "title"], unique = true),
    ],
)
data class AltTitleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tmdbId: Int,
    val mediaType: String,
    val title: String,
    val language: String? = null,
    val country: String? = null,
    /** `true` si proviene de traducciones (uso habitual en es-CO/es). */
    val fromTranslation: Boolean = false,
)
