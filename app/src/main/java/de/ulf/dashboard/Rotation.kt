package de.ulf.dashboard

/** Endless round-robin over a non-empty list of pages. Pure, no Android dependencies. */
class Rotation(private val pages: List<Page>) {
    private var index = 0

    init {
        require(pages.isNotEmpty()) { "Rotation needs at least one page - add an entry to dashboards.json" }
    }

    fun current(): Page = pages[index]

    fun advance(): Page {
        index = (index + 1) % pages.size
        return pages[index]
    }
}
