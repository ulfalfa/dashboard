package de.ulf.dashboard

import java.io.File
import java.io.IOException

/** Persists the page list as JSON. The file is seeded from [seedJson] on first use. */
class ConfigStore(private val file: File, private val seedJson: String) {
    @Synchronized
    fun load(): List<Page> {
        if (!file.exists()) write(seedJson)
        return DashboardConfig.parse(file.readText(Charsets.UTF_8))
    }

    @Synchronized
    fun save(pages: List<Page>) = write(DashboardConfig.toJson(pages))

    // Write to a temp file first so a crash mid-write never leaves a truncated config
    private fun write(json: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json, Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            throw IOException("Could not replace ${file.path} - check free storage space and retry")
        }
    }
}
