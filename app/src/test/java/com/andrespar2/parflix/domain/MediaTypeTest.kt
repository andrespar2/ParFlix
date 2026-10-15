package com.andrespar2.parflix.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Correspondencia con los tres listados de Vimeus y con TMDB. */
class MediaTypeTest {

    @Test
    fun `interpreta los tipos devueltos por Vimeus`() {
        assertEquals(MediaType.MOVIE, MediaType.fromString("movie"))
        assertEquals(MediaType.MOVIE, MediaType.fromString("MOVIES"))
        assertEquals(MediaType.MOVIE, MediaType.fromString("pelicula"))
        assertEquals(MediaType.SERIES, MediaType.fromString("series"))
        assertEquals(MediaType.SERIES, MediaType.fromString("tv"))
        assertEquals(MediaType.ANIME, MediaType.fromString("anime"))
        assertEquals(MediaType.ANIME, MediaType.fromString("Animes"))
    }

    @Test
    fun `valores desconocidos no inventan un tipo`() {
        assertNull(MediaType.fromString(null))
        assertNull(MediaType.fromString(""))
        assertNull(MediaType.fromString("juego"))
        assertNull(MediaType.fromVimeusSegment(null))
        assertNull(MediaType.fromVimeusSegment("pelicula"))
    }

    @Test
    fun `deduce el tipo a partir del segmento de insercion`() {
        assertEquals(MediaType.MOVIE, MediaType.fromVimeusSegment("movie"))
        assertEquals(MediaType.SERIES, MediaType.fromVimeusSegment("serie"))
        assertEquals(MediaType.ANIME, MediaType.fromVimeusSegment("anime"))
    }

    @Test
    fun `los segmentos coinciden con los endpoints de insercion`() {
        assertEquals("movie", MediaType.MOVIE.vimeusSegment)
        assertEquals("serie", MediaType.SERIES.vimeusSegment)
        assertEquals("anime", MediaType.ANIME.vimeusSegment)
    }

    @Test
    fun `los endpoints de listado coinciden con la API`() {
        assertEquals("api/listing/movies", MediaType.MOVIE.vimeusListing)
        assertEquals("api/listing/series", MediaType.SERIES.vimeusListing)
        assertEquals("api/listing/animes", MediaType.ANIME.vimeusListing)
    }

    @Test
    fun `anime y series son TV en TMDB`() {
        assertEquals("movie", MediaType.MOVIE.tmdbPath)
        assertEquals("tv", MediaType.SERIES.tmdbPath)
        assertEquals("tv", MediaType.ANIME.tmdbPath)
    }

    @Test
    fun `solo las peliculas no son por temporadas`() {
        assertFalse(MediaType.MOVIE.isShow)
        assertTrue(MediaType.SERIES.isShow)
        assertTrue(MediaType.ANIME.isShow)
    }
}
