package de.ulf.dashboard

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/** Stream-level export/import of the page list, used with the system file picker. */
object ConfigTransfer {
    private const val MAX_BYTES = 64 * 1024
    private const val BUFFER_BYTES = 8 * 1024

    fun export(pages: List<Page>, out: OutputStream) {
        out.write(DashboardConfig.toJson(pages).toByteArray(Charsets.UTF_8))
    }

    /** Reads and validates a config file. Does not save - the caller decides what to do with the result. */
    fun parseImport(input: InputStream): List<Page> {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(BUFFER_BYTES)
        while (buffer.size() <= MAX_BYTES) {
            val read = input.read(chunk)
            if (read == -1) break
            buffer.write(chunk, 0, read)
        }
        if (buffer.size() > MAX_BYTES) {
            throw ConfigException("File is larger than ${MAX_BYTES / 1024} KB - pick the exported dashboards.json", null)
        }
        // Editors on Windows like to prepend a BOM, which org.json rejects
        return DashboardConfig.parse(buffer.toString(Charsets.UTF_8.name()).removePrefix("﻿"))
    }
}
