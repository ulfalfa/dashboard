package de.ulf.dashboard

import org.json.JSONArray
import org.json.JSONObject

data class MemoryInfo(val availMb: Long, val totalMb: Long, val lowMemory: Boolean)

/**
 * Per-page load statistics for the portal's status view. Written on the UI thread, read by HTTP threads.
 * Every page keeps one WebView in memory, so [toJson] also warns when the page count or free memory gets critical.
 */
class StatusTracker(private val clock: () -> Long = System::currentTimeMillis) {
    private class Stats {
        var loadStartedAt: Long? = null
        var loadedAt: Long? = null
        var loadMs: Long? = null
        var loads = 0
        var failures = 0
        var errorAt: Long? = null
        var error: String? = null
    }

    private val bootAt = clock()
    private var stats = emptyList<Stats>()
    private var shown = NONE

    @Synchronized
    fun reset(pageCount: Int) {
        stats = List(pageCount) { Stats() }
        shown = NONE
    }

    @Synchronized
    fun loadStarted(index: Int) {
        stats.getOrNull(index)?.loadStartedAt = clock()
    }

    @Synchronized
    fun loaded(index: Int) {
        val s = stats.getOrNull(index) ?: return
        val now = clock()
        s.loadStartedAt?.let { s.loadMs = now - it }
        s.loadStartedAt = null
        s.loadedAt = now
        s.loads++
    }

    @Synchronized
    fun failed(index: Int, message: String) {
        val s = stats.getOrNull(index) ?: return
        s.loadStartedAt = null
        s.errorAt = clock()
        s.error = message
        s.failures++
    }

    @Synchronized
    fun shown(index: Int) {
        shown = index
    }

    @Synchronized
    fun toJson(pages: List<Page>, paused: Boolean, memory: MemoryInfo): String {
        val now = clock()
        val entries = JSONArray()
        pages.forEachIndexed { i, page ->
            val s = stats.getOrNull(i) ?: Stats()
            val loadedAt = s.loadedAt
            val errorAt = s.errorAt
            val state = when {
                errorAt != null && (loadedAt == null || errorAt >= loadedAt) -> "error"
                loadedAt != null -> "ok"
                else -> "pending"
            }
            entries.put(
                JSONObject().put("index", i).put("url", page.url).put("current", i == shown).put("state", state)
                    .put("loads", s.loads).put("failures", s.failures)
                    .put("loadMs", s.loadMs ?: JSONObject.NULL)
                    .put("loadedAgoSeconds", loadedAt?.let { (now - it) / 1000 } ?: JSONObject.NULL)
                    .put("error", s.error ?: JSONObject.NULL)
                    .put("errorAgoSeconds", errorAt?.let { (now - it) / 1000 } ?: JSONObject.NULL)
            )
        }
        return JSONObject()
            .put("uptimeSeconds", (now - bootAt) / 1000)
            .put("paused", paused)
            .put("pages", entries)
            .put(
                "memory",
                JSONObject().put("availMb", memory.availMb).put("totalMb", memory.totalMb)
                    .put("lowMemory", memory.lowMemory).put("webViews", pages.size)
            )
            .put("warnings", JSONArray(warnings(pages.size, memory)))
            .toString()
    }

    private fun warnings(pageCount: Int, memory: MemoryInfo): List<String> = buildList {
        if (pageCount > MAX_RECOMMENDED_PAGES) {
            add("$pageCount Seiten = $pageCount WebViews im Speicher (empfohlen: höchstens $MAX_RECOMMENDED_PAGES)")
        }
        if (memory.lowMemory) {
            add("Android meldet knappen Speicher - Seiten reduzieren oder Gerät neu starten")
        } else if (memory.availMb < LOW_MEMORY_MB) {
            add("Nur noch ${memory.availMb} MB freier Speicher")
        }
    }

    companion object {
        const val MAX_RECOMMENDED_PAGES = 8
        const val LOW_MEMORY_MB = 300
        private const val NONE = -1
    }
}
