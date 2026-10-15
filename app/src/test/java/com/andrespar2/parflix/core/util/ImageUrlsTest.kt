package com.andrespar2.parflix.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** URLs de imagen de TMDB (con atribución requerida en Acerca de). */
class ImageUrlsTest {

    @Test
    fun `poster con ruta TMDB absoluta`() {
        assertEquals(
            "https://image.tmdb.org/t/p/w500/abc.jpg",
            ImageUrls.poster("/abc.jpg"),
        )
        assertEquals(
            "https://image.tmdb.org/t/p/w500/abc.jpg",
            ImageUrls.poster("abc.jpg"),
        )
    }

    @Test
    fun `poster conserva URL completa del proveedor`() {
        assertEquals("https://cdn.ejemplo.com/p.jpg", ImageUrls.poster("https://cdn.ejemplo.com/p.jpg"))
    }

    @Test
    fun `sin imagen devuelve null`() {
        assertNull(ImageUrls.poster(null))
        assertNull(ImageUrls.poster("   "))
        assertNull(ImageUrls.backdrop(null))
    }

    @Test
    fun `backdrop usa tamanio panoramico`() {
        assertEquals(
            "https://image.tmdb.org/t/p/w780/bd.jpg",
            ImageUrls.backdrop("/bd.jpg", ImageUrls.BACKDROP),
        )
        assertEquals(
            "https://image.tmdb.org/t/p/w1280/bd.jpg",
            ImageUrls.backdrop("/bd.jpg", ImageUrls.BACKDROP_LARGE),
        )
    }

    @Test
    fun `still y profile usan sus tamanios`() {
        assertEquals("https://image.tmdb.org/t/p/w300/s.jpg", ImageUrls.still("/s.jpg"))
        assertEquals("https://image.tmdb.org/t/p/w185/p.jpg", ImageUrls.profile("/p.jpg"))
    }
}
