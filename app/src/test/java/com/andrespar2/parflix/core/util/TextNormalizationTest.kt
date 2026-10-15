package com.andrespar2.parflix.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Normalización de texto usada al indexar y al buscar. */
class TextNormalizationTest {

    @Test
    fun `normalize elimina tildes y diacriticos`() {
        assertEquals("amelie", TextNormalization.normalize("Amélie"))
        assertEquals("avengers endgame", TextNormalization.normalize("Ávengers Endgame"))
        assertEquals("naranja", TextNormalization.normalize("Ñaranja"))
    }

    @Test
    fun `normalize unifica mayusculas signos y espacios`() {
        assertEquals(
            "el senor de los anillos",
            TextNormalization.normalize("  EL SEÑOR DE LOS  ANILLOS!! "),
        )
        assertEquals("", TextNormalization.normalize(""))
        assertEquals("", TextNormalization.normalize(null))
        assertEquals("", TextNormalization.normalize("   "))
    }

    @Test
    fun `normalize conserva numeros`() {
        assertEquals("spider man 2", TextNormalization.normalize("Spider-Man 2"))
    }

    @Test
    fun `toFtsQuery convierte cada termino en prefijo`() {
        assertEquals("amo* el* cielo*", TextNormalization.toFtsQuery("Amo el cielo"))
        assertEquals("senor* de* los* anillos*", TextNormalization.toFtsQuery("señor de los anillos"))
    }

    @Test
    fun `toFtsQuery con texto vacio devuelve cadena vacia`() {
        assertEquals("", TextNormalization.toFtsQuery("!!!"))
        assertEquals("", TextNormalization.toFtsQuery(""))
    }

    @Test
    fun `levenshtein distancia clasica`() {
        assertEquals(0, TextNormalization.levenshtein("", ""))
        assertEquals(3, TextNormalization.levenshtein("kitten", "sitting"))
        assertEquals(1, TextNormalization.levenshtein("hola", "holas"))
        assertEquals(3, TextNormalization.levenshtein("", "abc"))
    }

    @Test
    fun `similarity mide coincidencia entre 0 y 1`() {
        assertEquals(1.0, TextNormalization.similarity("batman", "batman"), 0.0001)
        assertEquals(0.0, TextNormalization.similarity("", "batman"), 0.0001)
        val partial = TextNormalization.similarity("batmn", "batman")
        assertTrue("se esperaba similitud alta, fue $partial", partial > 0.7)
        assertTrue("similitud no puede superar 1", partial <= 1.0)
    }
}
