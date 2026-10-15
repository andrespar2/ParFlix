package com.andrespar2.parflix.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Búsqueda reciente del usuario (local, nunca se envía a ningún servidor). */
@Entity(
    tableName = "search_queries",
    indices = [Index(value = ["lastUsedAt"])],
)
data class SearchQueryEntity(
    @PrimaryKey val query: String,
    val normalized: String,
    val lastUsedAt: Long,
)
