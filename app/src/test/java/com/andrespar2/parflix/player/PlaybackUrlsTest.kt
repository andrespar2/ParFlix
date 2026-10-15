package com.andrespar2.parflix.player

import com.andrespar2.parflix.domain.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reglas de construcción de las URLs de reproducción oficiales de Vimeus.
 * Se comprueba que jamás se fabrique un origen distinto de `vimeus.com`.
 */
class PlaybackUrlsTest {

    @Test
    fun `pelicula con tmdb usa el segmento oficial`() {
        val url = PlaybackUrls.build(
            mediaType = MediaType.MOVIE,
            tmdbId = 603,
            imdbId = null,
            season = null,
            episode = null,
            viewKey = "vista123",
        )
        assertNotNull(url)
        assertTrue(url!!.startsWith("https://vimeus.com/e/movie"))
        assertTrue(url.contains("tmdb=603"))
        assertTrue(url.contains("view_key=vista123"))
        assertFalse(url.contains("https://vimeus.com/e/serie"))
    }

    @Test
    fun `serie requiere temporada y episodio`() {
        val url = PlaybackUrls.build(
            mediaType = MediaType.SERIES,
            tmdbId = 1399,
            imdbId = null,
            season = 2,
            episode = 5,
            viewKey = "vista123",
        )
        assertNotNull(url)
        assertTrue(url!!.startsWith("https://vimeus.com/e/serie"))
        assertTrue(url.contains("se=2"))
        assertTrue(url.contains("ep=5"))
    }

    @Test
    fun `serie sin temporada o episodio no se inventa`() {
        assertNull(
            PlaybackUrls.build(
                mediaType = MediaType.SERIES,
                tmdbId = 1399,
                imdbId = null,
                season = null,
                episode = null,
                viewKey = "vista123",
            )
        )
        assertNull(
            PlaybackUrls.build(
                mediaType = MediaType.ANIME,
                tmdbId = 1399,
                imdbId = null,
                season = 1,
                episode = null,
                viewKey = "vista123",
            )
        )
    }

    @Test
    fun `sin tmdb usa el identificador imdb`() {
        val url = PlaybackUrls.build(
            mediaType = MediaType.MOVIE,
            tmdbId = null,
            imdbId = "tt0111161",
            season = null,
            episode = null,
            viewKey = "vista123",
        )
        assertNotNull(url)
        assertTrue(url!!.contains("imdb=tt0111161"))
        assertFalse(url.contains("tmdb="))
    }

    @Test
    fun `sin identificadores no se inventa una url`() {
        assertNull(
            PlaybackUrls.build(
                mediaType = MediaType.MOVIE,
                tmdbId = null,
                imdbId = null,
                season = null,
                episode = null,
                viewKey = "vista123",
            )
        )
    }

    @Test
    fun `sin view_key y sin embed guardado no se produce url`() {
        assertNull(
            PlaybackUrls.build(
                mediaType = MediaType.MOVIE,
                tmdbId = 603,
                imdbId = null,
                season = null,
                episode = null,
                viewKey = "   ",
            )
        )
    }

    @Test
    fun `se prefiere el embed_url guardado cuando es valido`() {
        val stored = "https://vimeus.com/e/movie?tmdb=603&view_key=abc"
        val url = PlaybackUrls.build(
            mediaType = MediaType.MOVIE,
            tmdbId = 603,
            imdbId = null,
            season = null,
            episode = null,
            viewKey = "",
            storedEmbedUrl = stored,
        )
        assertEquals(stored, url)
    }

    @Test
    fun `un embed de otro tipo de contenido se rechaza`() {
        val wrongType = "https://vimeus.com/e/serie?tmdb=603"
        assertFalse(PlaybackUrls.isValid(wrongType, MediaType.MOVIE))
        val url = PlaybackUrls.build(
            mediaType = MediaType.MOVIE,
            tmdbId = 603,
            imdbId = null,
            season = null,
            episode = null,
            viewKey = "vista123",
            storedEmbedUrl = wrongType,
        )
        assertNotNull(url)
        assertTrue(url!!.startsWith("https://vimeus.com/e/movie"))
    }

    @Test
    fun `solo se acepta https del dominio autorizado`() {
        assertTrue(PlaybackUrls.isValid("https://vimeus.com/e/movie?tmdb=1", MediaType.MOVIE))
        assertTrue(PlaybackUrls.isValid("https://cdn.vimeus.com/e/movie?tmdb=1", MediaType.MOVIE))

        assertFalse(PlaybackUrls.isValid("http://vimeus.com/e/movie?tmdb=1", MediaType.MOVIE))
        assertFalse(PlaybackUrls.isValid("https://vimeus.evil.com/e/movie?tmdb=1", MediaType.MOVIE))
        assertFalse(PlaybackUrls.isValid("https://example.com/e/movie?tmdb=1", MediaType.MOVIE))
        assertFalse(PlaybackUrls.isValid("https://vimeus.com/e/serie?tmdb=1", MediaType.MOVIE))
        assertFalse(PlaybackUrls.isValid("not a url", MediaType.MOVIE))
        assertFalse(PlaybackUrls.isValid(null, MediaType.MOVIE))
        assertFalse(PlaybackUrls.isValid("", MediaType.MOVIE))
        // Ruta sin segmento `/e/...` no es una página de inserción.
        assertFalse(PlaybackUrls.isValid("https://vimeus.com/player?tmdb=1", MediaType.MOVIE))
    }
}
