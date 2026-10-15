package com.andrespar2.parflix.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Género de TMDB cacheado localmente (`/genre/movie/list`, `/genre/tv/list`). */
@Entity(
    tableName = "genres",
    primaryKeys = ["genreId", "mediaType"],
)
data class GenreEntity(
    val genreId: Int,
    val mediaType: String,
    val name: String,
)

/** Relación título↔género, poblada al enriquecer con TMDB (permite filas por género). */
@Entity(
    tableName = "genre_links",
    primaryKeys = ["catalogId", "genreId"],
    indices = [Index(value = ["genreId"])],
)
data class GenreLinkEntity(
    val catalogId: Long,
    val genreId: Int,
)

/**
 * Estado de sincronización por tipo de contenido.
 * `generation` es el contador de barridos completos usado para detectar
 * eliminaciones sin borrar datos por errores temporales.
 */
@Entity(tableName = "sync_state", primaryKeys = ["key"])
data class SyncStateEntity(
    val key: String,
    /** Última sincronización correcta (epoch millis). */
    val lastSyncAt: Long = 0,
    /** Último barrido completo (para reconciliación periódica). */
    val lastFullReconcileAt: Long = 0,
    /** Página por la que continuar en la próxima sincronización incremental. */
    val nextPage: Int = 1,
    /** Páginas totales conocidas del listado. */
    val totalPages: Int = 0,
    val generation: Long = 0,
    val lastError: String? = null,
)

/** Huella de cada página del listado para detectar cambios sin reescribir todo. */
@Entity(
    tableName = "sync_pages",
    primaryKeys = ["key"],
)
data class SyncPageEntity(
    val key: String,
    val fingerprint: String,
    val itemCount: Int,
    val updatedAt: Long,
)
