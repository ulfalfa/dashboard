package de.ulf.dashboard

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusTrackerTest {
    private var now = 1_000_000L
    private val tracker = StatusTracker { now }
    private val memory = MemoryInfo(availMb = 2000, totalMb = 4000, lowMemory = false)

    private fun pages(count: Int) = List(count) { Page("https://p$it", 10, 100, 0, true) }

    private fun status(pages: List<Page>, paused: Boolean = false, mem: MemoryInfo = memory) =
        JSONObject(tracker.toJson(pages, paused, mem))

    @Test
    fun pageIsPendingUntilLoaded() {
        tracker.reset(1)
        val page = status(pages(1)).getJSONArray("pages").getJSONObject(0)
        assertEquals("pending", page.getString("state"))
        assertTrue(page.isNull("loadedAgoSeconds"))
    }

    @Test
    fun loadedPageReportsLoadTimeAndAge() {
        tracker.reset(1)
        tracker.loadStarted(0)
        now += 1_500
        tracker.loaded(0)
        now += 10_000
        val page = status(pages(1)).getJSONArray("pages").getJSONObject(0)
        assertEquals("ok", page.getString("state"))
        assertEquals(1_500, page.getInt("loadMs"))
        assertEquals(10, page.getInt("loadedAgoSeconds"))
        assertEquals(1, page.getInt("loads"))
    }

    @Test
    fun failureMarksPageUntilTheNextSuccessfulLoad() {
        tracker.reset(1)
        tracker.failed(0, "HTTP 502")
        var page = status(pages(1)).getJSONArray("pages").getJSONObject(0)
        assertEquals("error", page.getString("state"))
        assertEquals("HTTP 502", page.getString("error"))
        assertEquals(1, page.getInt("failures"))
        now += 1_000
        tracker.loaded(0)
        page = status(pages(1)).getJSONArray("pages").getJSONObject(0)
        assertEquals("ok", page.getString("state"))
        assertEquals(1, page.getInt("failures")) // history stays
    }

    @Test
    fun marksShownPageAndPause() {
        tracker.reset(2)
        tracker.shown(1)
        val s = status(pages(2), paused = true)
        assertTrue(s.getBoolean("paused"))
        assertEquals(false, s.getJSONArray("pages").getJSONObject(0).getBoolean("current"))
        assertEquals(true, s.getJSONArray("pages").getJSONObject(1).getBoolean("current"))
    }

    @Test
    fun reportsTheShownOverlayPage() {
        tracker.reset(1)
        assertTrue(status(pages(1)).isNull("overlay"))
        val s = JSONObject(tracker.toJson(pages(1), false, memory, "https://spiegel.de"))
        assertEquals("https://spiegel.de", s.getString("overlay"))
    }

    @Test
    fun resetClearsStatisticsAndIgnoresStaleIndexes() {
        tracker.reset(2)
        tracker.failed(1, "boom")
        tracker.reset(1)
        tracker.failed(1, "late") // page 1 no longer exists
        val s = status(pages(1))
        assertEquals(1, s.getJSONArray("pages").length())
        assertEquals("pending", s.getJSONArray("pages").getJSONObject(0).getString("state"))
    }

    @Test
    fun warnsAboutTooManyPagesAndLowMemory() {
        tracker.reset(0)
        assertEquals(0, status(pages(StatusTracker.MAX_RECOMMENDED_PAGES)).getJSONArray("warnings").length())
        val many = status(pages(StatusTracker.MAX_RECOMMENDED_PAGES + 1))
        assertEquals(1, many.getJSONArray("warnings").length())
        assertEquals(9, many.getJSONObject("memory").getInt("webViews"))
        val low = status(pages(1), mem = MemoryInfo(availMb = 100, totalMb = 4000, lowMemory = false))
        assertTrue(low.getJSONArray("warnings").getString(0).contains("100 MB"))
        val critical = status(pages(1), mem = MemoryInfo(availMb = 100, totalMb = 4000, lowMemory = true))
        assertEquals(1, critical.getJSONArray("warnings").length())
    }
}
