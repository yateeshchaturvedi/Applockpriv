package com.yateeshpriv.applockpriv

import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class IntruderLogTest {

    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    @Test
    fun `parseIntruderFileName extracts timestamp and packageName from tagged file`() {
        val fileName = "intruder_20260908_174522_com.whatsapp.jpg"
        val (date, targetPackage) = parseIntruderFileName(fileName, dateFormat)

        assertNotNull("Date should not be null", date)
        assertEquals("Target package should match", "com.whatsapp", targetPackage)
    }

    @Test
    fun `parseIntruderFileName extracts complex package names with underscores`() {
        val fileName = "intruder_20260908_120000_org.example.app_secret.jpg"
        val (date, targetPackage) = parseIntruderFileName(fileName, dateFormat)

        assertNotNull("Date should not be null", date)
        assertEquals("org.example.app_secret", targetPackage)
    }

    @Test
    fun `parseIntruderFileName handles legacy file without package suffix`() {
        val fileName = "intruder_20260908_174522.jpg"
        val (date, targetPackage) = parseIntruderFileName(fileName, dateFormat)

        assertNotNull("Date should not be null for legacy format", date)
        assertNull("Target package should be null for legacy format", targetPackage)
    }

    @Test
    fun `parseIntruderFileName returns null date on malformed timestamp`() {
        val fileName = "intruder_invalidtime_com.app.jpg"
        val (date, targetPackage) = parseIntruderFileName(fileName, dateFormat)

        assertNull("Date should be null for invalid timestamp", date)
    }
}
