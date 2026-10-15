package com.andrespar2.parflix.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Utilidades de fecha/hora tolerantes a los distintos formatos de los proveedores. */
object TimeUtils {

    fun nowMillis(): Long = System.currentTimeMillis()

    fun today(): String = LocalDate.now().toString()

    /**
     * Interpreta `synced_at` y fechas ISO de Vimeus/TMDB sin asumir un formato
     * único: ISO con zona, ISO local, solo fecha, o epoch en segundos/milisegundos.
     */
    fun parseToEpochMillis(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val value = raw.trim()
        value.toLongOrNull()?.let { number ->
            return when {
                number > 100_000_000_000L -> number            // milisegundos
                number > 1_000_000_000L -> number * 1000        // segundos
                else -> null
            }
        }
        val parsers = listOf<() -> Instant>(
            { OffsetDateTime.parse(value).toInstant() },
            { LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant() },
            { Instant.parse(value) },
            { LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault()).toInstant() },
        )
        for (parser in parsers) {
            runCatching { return parser().toEpochMilli() }
        }
        return null
    }

    /** Fecha larga legible en español, o null si no se puede interpretar. */
    fun formatDate(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            LocalDate.parse(raw).format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy"))
        }.getOrNull() ?: raw
    }

    fun yearOf(date: String?): Int? {
        if (date.isNullOrBlank() || date.length < 4) return null
        return date.take(4).toIntOrNull()
    }
}
