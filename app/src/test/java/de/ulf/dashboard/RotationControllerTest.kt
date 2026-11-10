package de.ulf.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class RotationControllerTest {
    private class FakeLoader : PageLoader {
        val events = mutableListOf<String>()
        override fun load(index: Int) { events += "load$index" }
        override fun show(index: Int) { events += "show$index" }
        val loadedPages = mutableListOf<Int>()
        val timedOutPages = mutableListOf<Int>()
        override fun loaded(index: Int) { loadedPages += index }
        override fun timedOut(index: Int) { timedOutPages += index }
    }

    private class FakeTimer : Timer {
        var delayMs: Long? = null
        private var action: (() -> Unit)? = null
        override fun start(delayMs: Long, action: () -> Unit) { this.delayMs = delayMs; this.action = action }
        override fun cancel() { delayMs = null; action = null }
        fun fire() { val a = action; cancel(); a?.invoke() }
    }

    private val loader = FakeLoader()
    private val dwell = FakeTimer()
    private val prepare = FakeTimer()

    private fun controller(vararg pages: Page) =
        RotationController(pages.toList(), loader, dwell, prepare, 30_000L, 5_000L)

    private fun page(seconds: Int, renderDelay: Int, reload: Boolean) = Page("https://x", seconds, 100, renderDelay, reload)

    @Test
    fun loadsFirstPageWithLoadTimeoutAndShowsItWhenLoaded() {
        val c = controller(page(10, 0, true), page(20, 0, true))
        c.start()
        assertEquals(listOf("load0"), loader.events)
        assertEquals(30_000L, prepare.delayMs)
        c.onPageLoaded(0)
        assertEquals(listOf("load0", "show0", "load1"), loader.events) // next page preloads right away
        assertEquals(10_000L, dwell.delayMs)
    }

    @Test
    fun switchesToPreloadedPageWhenDwellIsOver() {
        val c = controller(page(10, 0, true), page(20, 0, true))
        c.start()
        c.onPageLoaded(0)
        c.onPageLoaded(1)
        assertEquals(listOf("load0", "show0", "load1"), loader.events) // still waiting for the dwell time
        dwell.fire()
        assertEquals(listOf("load0", "show0", "load1", "show1", "load0"), loader.events)
        assertEquals(20_000L, dwell.delayMs)
    }

    @Test
    fun waitsForNextPageIfDwellIsOverBeforeItLoaded() {
        val c = controller(page(10, 0, true), page(20, 0, true))
        c.start()
        c.onPageLoaded(0)
        dwell.fire()
        assertEquals(listOf("load0", "show0", "load1"), loader.events)
        c.onPageLoaded(1)
        assertEquals("show1", loader.events[3])
    }

    @Test
    fun renderDelayHappensInBackgroundBeforeShowing() {
        val c = controller(page(10, 3, true), page(20, 0, true))
        c.start()
        c.onPageLoaded(0)
        assertEquals(listOf("load0"), loader.events)
        assertEquals(3_000L, prepare.delayMs)
        prepare.fire()
        assertEquals(listOf("load0", "show0", "load1"), loader.events)
    }

    @Test
    fun pageWithoutReloadIsOnlyLoadedOnce() {
        val c = controller(page(10, 0, false), page(10, 0, false))
        c.start()
        c.onPageLoaded(0)
        c.onPageLoaded(1)
        dwell.fire() // show1, page 0 already loaded -> no load0
        dwell.fire() // show0 again, no reload
        assertEquals(listOf("load0", "show0", "load1", "show1", "show0"), loader.events)
    }

    @Test
    fun loadTimeoutIsCancelledOnceThePageIsReady() {
        val c = controller(page(8, 0, false), page(30, 0, true))
        c.start()
        c.onPageLoaded(0)
        c.onPageLoaded(1)
        assertEquals(null, prepare.delayMs) // a stale timeout would later fail the page prepared next
        dwell.fire()
        assertEquals(null, prepare.delayMs) // page 0 needs no reload, so it is ready without a timeout
        dwell.fire()
        assertEquals(listOf("show0", "show1", "show0"), loader.events.filter { it.startsWith("show") })
    }

    @Test
    fun failedPageIsSkippedAfterErrorDelayWhileCurrentStaysVisible() {
        val c = controller(page(10, 0, true), page(10, 0, true), page(10, 0, true))
        c.start()
        c.onPageLoaded(0)
        c.onPageFailed(1)
        assertEquals(5_000L, prepare.delayMs)
        prepare.fire()
        assertEquals(listOf("load0", "show0", "load1", "load2"), loader.events)
        c.onPageLoaded(2)
        dwell.fire()
        assertEquals("show2", loader.events.last { it.startsWith("show") })
    }

    @Test
    fun pageThatNeverFinishesLoadingIsSkipped() {
        val c = controller(page(10, 0, true), page(10, 0, true))
        c.start()
        prepare.fire() // load timeout of page 0
        assertEquals(5_000L, prepare.delayMs)
        prepare.fire()
        assertEquals(listOf("load0", "load1"), loader.events)
    }

    @Test
    fun ignoresEventsOfPagesThatAreNotBeingPrepared() {
        val c = controller(page(10, 0, true), page(10, 0, true))
        c.start()
        c.onPageLoaded(1) // wrong page
        assertEquals(listOf("load0"), loader.events)
        c.onPageFailed(0)
        c.onPageLoaded(0) // finished after error
        assertEquals(5_000L, prepare.delayMs)
        assertEquals(listOf("load0"), loader.events)
    }

    @Test
    fun singlePageWithReloadReloadsInPlaceEachCycle() {
        val c = controller(page(10, 0, true))
        c.start()
        c.onPageLoaded(0)
        assertEquals(listOf("load0", "show0", "load0"), loader.events)
        c.onPageLoaded(0)
        dwell.fire()
        assertEquals(listOf("load0", "show0", "load0", "show0", "load0"), loader.events)
    }

    @Test
    fun stopCancelsTimersAndIgnoresEvents() {
        val c = controller(page(10, 0, true))
        c.start()
        c.stop()
        assertEquals(null, prepare.delayMs)
        assertEquals(null, dwell.delayMs)
        c.onPageLoaded(0)
        assertEquals(listOf("load0"), loader.events)
    }

    private fun shows() = loader.events.filter { it.startsWith("show") }

    @Test
    fun reportsLoadedAndTimedOutPagesToTheLoader() {
        val c = controller(page(10, 0, true), page(10, 0, true))
        c.start()
        c.onPageLoaded(0)
        prepare.fire() // load timeout of page 1
        assertEquals(listOf(0), loader.loadedPages)
        assertEquals(listOf(1), loader.timedOutPages)
    }

    @Test
    fun pauseKeepsCurrentPageAndResumeRestartsDwell() {
        val c = controller(page(10, 0, true), page(20, 0, true))
        c.start()
        c.onPageLoaded(0)
        c.pause()
        assertEquals(true, c.isPaused)
        assertEquals(null, dwell.delayMs)
        c.onPageLoaded(1) // preloading continues while paused
        assertEquals(listOf("load0", "show0", "load1"), loader.events)
        c.resume()
        assertEquals(false, c.isPaused)
        assertEquals(10_000L, dwell.delayMs)
    }

    @Test
    fun nextSkipsDwellTimeAndStaysPausedIfPaused() {
        val c = controller(page(10, 0, true), page(20, 0, true))
        c.start()
        c.onPageLoaded(0)
        c.pause()
        c.onPageLoaded(1)
        c.next()
        assertEquals(listOf("show0", "show1"), shows())
        assertEquals(null, dwell.delayMs) // still paused
        c.resume()
        assertEquals(20_000L, dwell.delayMs)
    }

    @Test
    fun nextWaitsForNextPageIfItIsNotLoadedYet() {
        val c = controller(page(10, 0, true), page(20, 0, true))
        c.start()
        c.onPageLoaded(0)
        c.next()
        assertEquals(listOf("show0"), shows())
        c.onPageLoaded(1)
        assertEquals(listOf("show0", "show1"), shows())
    }

    @Test
    fun previousGoesBackAndWrapsAround() {
        val c = controller(page(10, 0, true), page(20, 0, true))
        c.start()
        c.onPageLoaded(0)
        c.previous() // wraps to page 1, which is loaded anew
        assertEquals("load1", loader.events.last())
        c.onPageLoaded(1)
        assertEquals(listOf("show0", "show1"), shows())
        assertEquals(20_000L, dwell.delayMs)
    }

    @Test
    fun reloadLoadsCurrentPageAgainAndShowsItWhenDone() {
        val c = controller(page(10, 0, false), page(20, 0, false))
        c.start()
        c.onPageLoaded(0)
        c.onPageLoaded(1)
        c.reload()
        assertEquals("load0", loader.events.last())
        c.onPageLoaded(0)
        assertEquals(listOf("show0", "show0"), shows())
    }

    @Test
    fun controlBeforeFirstPageIsShownDoesNothing() {
        val c = controller(page(10, 0, true))
        c.start()
        c.next()
        c.previous()
        c.reload()
        assertEquals(listOf("load0"), loader.events)
    }

    @Test
    fun stopResetsPause() {
        val c = controller(page(10, 0, true))
        c.start()
        c.pause()
        c.stop()
        assertEquals(false, c.isPaused)
    }
}
