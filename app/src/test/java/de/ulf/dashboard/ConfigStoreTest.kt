package de.ulf.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ConfigStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val seed = """{"pages":[{"url":"https://seed","seconds":10,"zoom":100,"renderDelaySeconds":0,"reload":true}]}"""

    private fun store(file: File) = ConfigStore(file, seed)

    @Test
    fun seedsFileOnFirstLoad() {
        val file = File(tmp.root, "dashboards.json")
        assertEquals(listOf(Page("https://seed", 10, 100, 0, true)), store(file).load())
        assertEquals(true, file.exists())
    }

    @Test
    fun savedPagesSurviveNewStoreInstance() {
        val file = File(tmp.root, "dashboards.json")
        val pages = listOf(Page("https://a", 7, 120, 0, true))
        store(file).save(pages)
        assertEquals(pages, store(file).load())
    }

    @Test
    fun saveLeavesNoTempFileBehind() {
        val file = File(tmp.root, "dashboards.json")
        store(file).save(listOf(Page("https://a", 7, 120, 0, true)))
        assertFalse(File(tmp.root, "dashboards.json.tmp").exists())
    }

    @Test
    fun corruptFileSurfacesConfigException() {
        val file = File(tmp.root, "dashboards.json").apply { writeText("garbage") }
        assertThrows(ConfigException::class.java) { store(file).load() }
    }
}
