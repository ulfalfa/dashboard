package de.ulf.dashboard

/** One dashboard page: how long it stays on screen once loaded and at which zoom (percent). */
data class Page(val url: String, val seconds: Int, val zoom: Int)
