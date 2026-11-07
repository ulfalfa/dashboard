package de.ulf.dashboard

interface PageLoader {
    fun load(page: Page)
}

/** Single-slot timer: start() replaces any pending callback. */
interface Timer {
    fun start(delayMs: Long, action: () -> Unit)
    fun cancel()
}

/**
 * State machine: LOADING -> (loaded) -> SHOWING -> (dwell elapsed) -> next page.
 * Failures and load timeouts skip to the next page, so one broken dashboard never blocks the rotation.
 * Events outside LOADING (e.g. a late callback of the previous page) are ignored.
 */
class RotationController(
    private val rotation: Rotation,
    private val loader: PageLoader,
    private val timer: Timer,
    private val loadTimeoutMs: Long,
    private val errorDelayMs: Long,
) {
    private enum class State { IDLE, LOADING, SHOWING }

    private var state = State.IDLE

    fun start() = showCurrent()

    fun stop() {
        timer.cancel()
        state = State.IDLE
    }

    fun onPageLoaded() {
        if (state != State.LOADING) return
        state = State.SHOWING
        timer.start(rotation.current().seconds * 1000L) { showNext() }
    }

    fun onPageFailed() {
        if (state != State.LOADING) return
        state = State.SHOWING // hold briefly so the error is visible, then move on
        timer.start(errorDelayMs) { showNext() }
    }

    private fun showNext() {
        rotation.advance()
        showCurrent()
    }

    private fun showCurrent() {
        state = State.LOADING
        loader.load(rotation.current())
        timer.start(loadTimeoutMs) { showNext() }
    }
}
