package de.ulf.dashboard

/**
 * One dashboard page: [seconds] on screen at [zoom] percent. The page is loaded in the background while the
 * previous one is shown; [renderDelaySeconds] is the extra wait after the load event for late-rendering content
 * (e.g. Grafana panels). With [reload] the page is loaded again before every appearance, otherwise only once.
 * With [desktop] the WebView sends a desktop user agent, so sites serve their desktop instead of the mobile layout.
 */
data class Page(
    val url: String,
    val seconds: Int,
    val zoom: Int,
    val renderDelaySeconds: Int,
    val reload: Boolean,
    val desktop: Boolean = true,
)
