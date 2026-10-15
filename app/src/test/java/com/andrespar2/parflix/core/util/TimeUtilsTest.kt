package com.andrespar2.parflix.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Interpretación tolerante de fechas de los proveedores. */
class TimeUtilsTest {

    @Test
    fun `epoch en segundos se convierte a milisegundos`() {
        assertEquals(1_700_000_000_000L, TimeUtils.parseToEpochMillis("1700000000"))
    }

    @Test
    fun `epoch en milisegundos se conserva`() {
        assertEquals(1_700_000_000_000L, TimeUtils.parseToEpochMillis("1700000000000"))
    }

    @Test
    fun `valores nulos vacios o absurdos devuelven null`() {
        assertNull(TimeUtils.parseToEpochMillis(null))
        assertNull(TimeUtils.parseToEpochMillis(""))
        assertNull(TimeUtils.parseToEpochMillis("   "))
        assertNull(TimeUtils.parseToEpochMillis("12")) // por debajo del umbral
        assertNull(TimeUtils.parseToEpochMillis("no es fecha"))
    }

    @Test
    fun `fecha ISO con zona se interpreta`() {
        val millis = TimeUtils.parseToEpochMillis("2024-05-10T12:30:00Z")
        assertEquals(java.time.Instant.parse("2024-05-10T12:30:00Z").toEpochMilli(), millis)
    }

    @Test
    fun `solo fecha se interpreta como medianoche local`() {
        val millis = TimeUtils.parseToEpochMillis("2024-05-10")
        val expected = LocalDate.of(2024, 5, 10)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expected, millis)
    }

    @Test
    fun `yearOf extrae el anio y tolera basura`() {
        assertEquals(2024, TimeUtils.yearOf("2024-05-10"))
        assertEquals(1999, TimeUtils.yearOf("1999"))
        assertNull(TimeUtils.yearOf(null))
        assertNull(TimeUtils.yearOf(""))
        assertNull(TimeUtils.yearOf("20"))
    }

    @Test
    fun `formatDate no lanza excepciones con fechas invalidas`() {
        assertEquals("texto crudo", TimeUtils.formatDate("texto crudo"))
        assertNull(TimeUtils.formatDate(null))
        val formatted = TimeUtils.formatDate("2024-05-10")
        assertTrue(formatted == null || formatted.contains("2024"))
    }
}
