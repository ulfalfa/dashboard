package de.ulf.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class RotationControllerTest {
    private class FakeLoader : PageLoader {
        val loaded = mutableListOf<Page>()
        override fun load(page: Page) { loaded += page }
    }

    private class FakeTimer : Timer {
        var delayMs: Long? = null
        private var action: (() -> Unit)? = null
        override fun start(delayMs: Long, action: () -> Unit) { this.delayMs = delayMs; this.action = action }
        override fun cancel() { delayMs = null; action = null }
        fun fire() { val a = action; cancel(); a?.invoke() }
    }

    private val loader = FakeLoader()
    private val timer = FakeTimer()
    private val controller = RotationController(
        Rotation(listOf(Page("https://a", 10, 100, 0), Page("https://b", 20, 150, 0))), loader, timer, 30_000L, 5_000L
    )

    private fun urls(): List<String> = loader.loaded.map { it.url }

    @Test
    fun passesPerPageZoomToLoader() {
        controller.start()
        controller.onPageLoaded()
        timer.fire()
        assertEquals(listOf(100, 150), loader.loaded.map { it.zoom })
    }

    @Test
    fun waitsRenderDelayBeforeDwellStarts() {
        val delayed = RotationController(
            Rotation(listOf(Page("https://a", 10, 100, 3), Page("https://b", 20, 150, 0))), loader, timer, 30_000L, 5_000L
        )
        delayed.start()
        delayed.onPageLoaded()
        assertEquals(3_000L, timer.delayMs)
        timer.fire()
        assertEquals(10_000L, timer.delayMs)
        assertEquals(listOf("https://a"), urls())
        timer.fire()
        assertEquals(listOf("https://a", "https://b"), urls())
    }

    @Test
    fun ignoresFailureDuringRenderDelay() {
        val delayed = RotationController(
            Rotation(listOf(Page("https://a", 10, 100, 3))), loader, timer, 30_000L, 5_000L
        )
        delayed.start()
        delayed.onPageLoaded()
        delayed.onPageFailed()
        assertEquals(3_000L, timer.delayMs)
    }

    @Test
    fun loadsFirstPageWithLoadTimeout() {
        controller.start()
        assertEquals(listOf("https://a"), urls())
        assertEquals(30_000L, timer.delayMs)
    }

    @Test
    fun dwellsForConfiguredSecondsThenAdvances() {
        controller.start()
        controller.onPageLoaded()
        assertEquals(10_000L, timer.delayMs)
        timer.fire()
        assertEquals(listOf("https://a", "https://b"), urls())
    }

    @Test
    fun skipsFailedPageAfterErrorDelay() {
        controller.start()
        controller.onPageFailed()
        assertEquals(5_000L, timer.delayMs)
        timer.fire()
        assertEquals(listOf("https://a", "https://b"), urls())
    }

    @Test
    fun skipsPageThatNeverFinishesLoading() {
        controller.start()
        timer.fire() // load timeout
        assertEquals(listOf("https://a", "https://b"), urls())
    }

    @Test
    fun ignoresPageFinishedAfterFailure() {
        controller.start()
        controller.onPageFailed()
        controller.onPageLoaded() // WebView reports finished after an error
        assertEquals(5_000L, timer.delayMs)
    }

    @Test
    fun stopCancelsPendingTimer() {
        controller.start()
        controller.stop()
        assertEquals(null, timer.delayMs)
    }
}
