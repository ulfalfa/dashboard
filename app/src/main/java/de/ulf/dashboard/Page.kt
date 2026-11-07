package de.ulf.dashboard

/**
 * One dashboard page: zoom (percent), [renderDelaySeconds] to wait after the load event for late-rendering
 * content (e.g. Grafana panels), then [seconds] on screen.
 */
data class Page(val url: String, val seconds: Int, val zoom: Int, val renderDelaySeconds: Int)
