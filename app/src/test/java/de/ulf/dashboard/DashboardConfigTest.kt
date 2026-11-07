package de.ulf.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class DashboardConfigTest {
    @Test
    fun parsesPagesInOrder() {
        val pages = DashboardConfig.parse(
            """{"pages":[{"url":"https://a","seconds":10,"zoom":100,"renderDelaySeconds":0},{"url":"http://b","seconds":20,"zoom":75,"renderDelaySeconds":0}]}"""
        )
        assertEquals(listOf(Page("https://a", 10, 100, 0), Page("http://b", 20, 75, 0)), pages)
    }

    @Test
    fun rejectsMalformedJson() {
        assertThrows(ConfigException::class.java) { DashboardConfig.parse("not json") }
    }

    @Test
    fun rejectsEmptyPages() {
        assertThrows(ConfigException::class.java) { DashboardConfig.parse("""{"pages":[]}""") }
    }

    @Test
    fun rejectsUrlWithoutHttpScheme() {
        assertThrows(ConfigException::class.java) {
            DashboardConfig.parse("""{"pages":[{"url":"ftp://a","seconds":10,"zoom":100,"renderDelaySeconds":0}]}""")
        }
    }

    @Test
    fun rejectsNonPositiveSeconds() {
        assertThrows(ConfigException::class.java) {
            DashboardConfig.parse("""{"pages":[{"url":"https://a","seconds":0,"zoom":100,"renderDelaySeconds":0}]}""")
        }
    }

    @Test
    fun rejectsMissingFields() {
        assertThrows(ConfigException::class.java) {
            DashboardConfig.parse("""{"pages":[{"url":"https://a"}]}""")
        }
    }

    @Test
    fun rejectsZoomOutOfRange() {
        listOf(9, 501).forEach { zoom ->
            assertThrows(ConfigException::class.java) {
                DashboardConfig.parse("""{"pages":[{"url":"https://a","seconds":10,"zoom":$zoom,"renderDelaySeconds":0}]}""")
            }
        }
    }

    @Test
    fun rejectsMissingZoom() {
        assertThrows(ConfigException::class.java) {
            DashboardConfig.parse("""{"pages":[{"url":"https://a","seconds":10}]}""")
        }
    }

    @Test
    fun rejectsMissingRenderDelay() {
        val e = assertThrows(ConfigException::class.java) {
            DashboardConfig.parse("""{"pages":[{"url":"https://a","seconds":10,"zoom":100}]}""")
        }
        assertTrue(e.message!!.contains("renderDelaySeconds"))
    }

    @Test
    fun rejectsNegativeRenderDelay() {
        assertThrows(ConfigException::class.java) {
            DashboardConfig.parse("""{"pages":[{"url":"https://a","seconds":10,"zoom":100,"renderDelaySeconds":-1}]}""")
        }
    }

    @Test
    fun roundTripsThroughJson() {
        val pages = listOf(Page("https://a", 10, 100, 0), Page("https://b", 5, 200, 4))
        assertEquals(pages, DashboardConfig.parse(DashboardConfig.toJson(pages)))
    }
}
