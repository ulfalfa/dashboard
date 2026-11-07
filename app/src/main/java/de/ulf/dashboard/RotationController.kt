package de.ulf.dashboard

/** Loads and shows the WebView of the page at [index] in the configured page list. */
interface PageLoader {
    /** Starts loading in the background; the page is not visible until [show]. */
    fun load(index: Int)

    fun show(index: Int)
}

/** Single-slot timer: start() replaces any pending callback. */
interface Timer {
    fun start(delayMs: Long, action: () -> Unit)
    fun cancel()
}

/**
 * Preloading rotation. While page N is shown (dwell timer), page N+1 is prepared in the background:
 * LOADING -> (loaded) -> RENDERING (render delay, skipped if 0) -> READY. When the dwell time is over and the next
 * page is ready, it is shown. Pages with `reload = false` that were loaded before are READY immediately.
 * A failed or timed-out page is skipped after [errorDelayMs]; the current page stays visible meanwhile.
 * Events for a page that is not being prepared (e.g. late callbacks) are ignored.
 */
class RotationController(
    private val pages: List<Page>,
    private val loader: PageLoader,
    private val dwellTimer: Timer,
    private val prepareTimer: Timer,
    private val loadTimeoutMs: Long,
    private val errorDelayMs: Long,
) {
    private enum class Prep { IDLE, LOADING, RENDERING, READY, FAILED }

    private val loaded = BooleanArray(pages.size)
    private var shown = NONE
    private var preparing = 0
    private var prep = Prep.IDLE
    private var dwellElapsed = false

    init {
        require(pages.isNotEmpty()) { "RotationController needs at least one page" }
    }

    fun start() {
        stop()
        prepare(0)
    }

    fun stop() {
        dwellTimer.cancel()
        prepareTimer.cancel()
        prep = Prep.IDLE
        shown = NONE
        dwellElapsed = false
        loaded.fill(false)
    }

    fun onPageLoaded(index: Int) {
        if (prep != Prep.LOADING || index != preparing) return
        loaded[index] = true
        val delaySeconds = pages[index].renderDelaySeconds
        if (delaySeconds > 0) {
            prep = Prep.RENDERING
            prepareTimer.start(delaySeconds * 1000L) { ready() }
        } else {
            ready()
        }
    }

    fun onPageFailed(index: Int) {
        if (prep != Prep.LOADING || index != preparing) return
        failed()
    }

    private fun prepare(index: Int) {
        preparing = index
        if (loaded[index] && !pages[index].reload) {
            ready()
            return
        }
        prep = Prep.LOADING
        loaded[index] = false
        loader.load(index)
        prepareTimer.start(loadTimeoutMs) { failed() }
    }

    private fun failed() {
        prep = Prep.FAILED
        prepareTimer.start(errorDelayMs) { prepare(nextIndex(preparing)) }
    }

    private fun ready() {
        prepareTimer.cancel() // the load timeout of this page must not fire later and fail the next one
        prep = Prep.READY
        switchIfDue()
    }

    private fun switchIfDue() {
        if (prep != Prep.READY) return
        if (shown != NONE && !dwellElapsed) return
        shown = preparing
        dwellElapsed = false
        loader.show(shown)
        dwellTimer.start(pages[shown].seconds * 1000L) {
            dwellElapsed = true
            switchIfDue()
        }
        prepare(nextIndex(shown))
    }

    private fun nextIndex(index: Int): Int = (index + 1) % pages.size

    private companion object {
        const val NONE = -1
    }
}
