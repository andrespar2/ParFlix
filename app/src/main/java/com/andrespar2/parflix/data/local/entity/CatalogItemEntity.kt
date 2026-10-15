package com.andrespar2.parflix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import com.andrespar2.parflix.domain.MediaType

/**
 * Un título del catálogo local (película, serie o anime).
 *
 * Es la tabla central: se rellena desde Vimeus y se enriquece con TMDB.
 * La clave natural es (tmdbId, mediaType); `id` es la clave local usada por FTS.
 */
@Entity(
    tableName = "catalog_items",
    indices = [
        Index(value = ["tmdbId", "mediaType"], unique = true),
        Index(value = ["mediaType", "addedAt"]),
        Index(value = ["mediaType", "voteAverage"]),
        Index(value = ["mediaType", "popularity"]),
        Index(value = ["mediaType", "lastSeenAt"]),
        Index(value = ["imdbId"]),
        Index(value = ["tmdbId"]),
    ],
)
data class CatalogItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tmdbId: Int,
    /** Valor de [MediaType.name]. */
    val mediaType: String,
    val imdbId: String? = null,
    val title: String,
    val titleNormalized: String = "",
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val overview: String? = null,
    val quality: String? = null,
    /** URL de reproducción de Vimeus (contiene `view_key`; solo uso local). */
    val embedUrl: String? = null,
    /** `synced_at` del proveedor, si viene informado. */
    val syncedAt: Long? = null,
    /** Primera vez que este dispositivo vio el título (orden "agregados recientemente"). */
    val addedAt: Long,
    /** Generación de la última sincronización que lo vio (control de eliminaciones). */
    val lastSeenAt: Long = 0,
    val year: Int? = null,
    val voteAverage: Double? = null,
    val voteCount: Int? = null,
    val popularity: Double? = null,
    val totalSeasons: Int? = null,
    val totalEpisodes: Int? = null,
    /** `true` cuando los metadatos de TMDB ya se descargaron. */
    val enriched: Boolean = false,
    val lastEnrichedAt: Long? = null,
) {
    val type: MediaType? get() = MediaType.fromString(mediaType)
}

/**
 * Tabla FTS4 de búsqueda. `rowid` coincide con [CatalogItemEntity.id].
 * Se mantiene manualmente (altas/bajas) para incluir títulos alternativos de TMDB.
 * El texto se guarda normalizado (sin tildes, minúsculas) para búsquedas
 * insensibles a acentos y mayúsculas.
 */
@Fts4
@Entity(tableName = "catalog_fts")
data class CatalogFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long = 0,
    /** Título principal normalizado. */
    val title: String,
    /** Título original normalizado. */
    val originalTitle: String = "",
    /** Títulos alternativos normalizados, separados por espacio. */
    val altTitles: String = "",
    /** Concatenación de todos los nombres (para similitud/errores ortográficos). */
    val names: String = "",
    val imdbId: String = "",
    val tmdbId: String = "",
    val year: String = "",
)
