package com.andrespar2.parflix.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Estado de las credenciales efectivas. */
class CredentialsTest {

    @Test
    fun `credenciales vacias no son utilizables`() {
        val c = Credentials()
        assertTrue(c.isEmpty)
        assertFalse(c.hasVimeus)
        assertFalse(c.hasTmdb)
        assertFalse(c.isComplete)
    }

    @Test
    fun `solo vimeus es insuficiente`() {
        val c = Credentials(vimeusApiKey = "vimeus-api", vimeusViewKey = "view")
        assertTrue(c.hasVimeus)
        assertFalse(c.hasTmdb)
        assertFalse(c.isComplete)
        assertFalse(c.isEmpty)
    }

    @Test
    fun `solo tmdb es insuficiente`() {
        val c = Credentials(tmdbReadAccessToken = "token")
        assertFalse(c.hasVimeus)
        assertTrue(c.hasTmdb)
        assertFalse(c.isComplete)
    }

    @Test
    fun `ambos proveedores completa las credenciales`() {
        val c = Credentials(
            vimeusApiKey = "vimeus-api",
            vimeusViewKey = "view",
            tmdbReadAccessToken = "token",
        )
        assertTrue(c.hasVimeus)
        assertTrue(c.hasTmdb)
        assertTrue(c.isComplete)
        assertFalse(c.isEmpty)
    }

    @Test
    fun `tmdb por api key tambien vale`() {
        val c = Credentials(
            vimeusApiKey = "vimeus-api",
            vimeusViewKey = "view",
            tmdbApiKey = "key",
        )
        assertTrue(c.hasTmdb)
        assertTrue(c.isComplete)
    }

    @Test
    fun `espacios en blanco no cuentan como definidos`() {
        val c = Credentials(vimeusApiKey = "   ", vimeusViewKey = "   ")
        assertFalse(c.hasVimeus)
        assertFalse(c.isComplete)
        assertEquals("   ", c.vimeusApiKey)
    }
}
