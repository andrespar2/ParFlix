package com.andrespar2.parflix.core.util

import java.text.Normalizer
import java.util.Locale

/**
 * Normalización de texto para búsquedas locales:
 * - minúsculas
 * - sin tildes ni diacríticos
 * - sin signos de puntuación
 * - espacios colapsados
 *
 * El mismo criterio se aplica al indexar y al consultar, de modo que
 * "Amëlie", "amelie" y "AMELIE" coinciden.
 */
object TextNormalization {

    private val diacritics = Regex("\\p{Mn}+")
    private val nonAlphanumeric = Regex("[^a-z0-9 ]+")
    private val multiSpace = Regex("\\s+")

    fun normalize(input: String?): String {
        if (input.isNullOrBlank()) return ""
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
        return decomposed
            .lowercase(Locale.ROOT)
            .replace(diacritics, "")
            .replace(nonAlphanumeric, " ")
            .replace(multiSpace, " ")
            .trim()
    }

    /**
     * Convierte una consulta de usuario en una expresión FTS segura:
     * cada término se convierte en prefijo (`termino*`) y se escapan las comillas.
     */
    fun toFtsQuery(query: String): String {
        val normalized = normalize(query)
        if (normalized.isEmpty()) return ""
        return normalized
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { "${it.replace("\"", "")}*" }
    }

    /** Distancia de Levenshtein clásica, tolerante a errores ortográficos leves. */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    /**
     * Similitud en [0,1] entre dos cadenas ya normalizadas. Se usa como
     * respaldo cuando la búsqueda FTS no devuelve resultados (errores leves).
     */
    fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val maxLength = maxOf(a.length, b.length)
        return 1.0 - levenshtein(a, b).toDouble() / maxLength
    }
}
