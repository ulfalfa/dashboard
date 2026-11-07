package de.ulf.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ConfigTransferTest {
    private val pages = listOf(Page("https://a", 10, 100, 0), Page("https://b", 20, 150, 0))

    private fun parse(bytes: ByteArray): List<Page> = ConfigTransfer.parseImport(ByteArrayInputStream(bytes))

    @Test
    fun exportThenImportRoundTrips() {
        val out = ByteArrayOutputStream()
        ConfigTransfer.export(pages, out)
        assertEquals(pages, parse(out.toByteArray()))
    }

    @Test
    fun importAcceptsUtf8Bom() {
        val json = DashboardConfig.toJson(pages)
        assertEquals(pages, parse(("﻿" + json).toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun importRejectsInvalidContent() {
        assertThrows(ConfigException::class.java) { parse("not json".toByteArray()) }
    }

    @Test
    fun importRejectsOversizedFile() {
        assertThrows(ConfigException::class.java) { parse(ByteArray(70 * 1024) { ' '.code.toByte() }) }
    }
}
