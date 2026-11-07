package de.ulf.dashboard

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class ConfigException(message: String, cause: Throwable?) : Exception(message, cause)

/** Parses/serializes dashboards.json: {"pages":[{"url":"https://...","seconds":30,"zoom":100,"renderDelaySeconds":0,"reload":true}, ...]} */
object DashboardConfig {
    private const val MIN_SECONDS = 1
    private const val MIN_RENDER_DELAY = 0
    private const val MIN_ZOOM = 10
    private const val MAX_ZOOM = 500

    fun parse(json: String): List<Page> {
        val pages = readPages(json)
        if (pages.length() == 0) {
            throw ConfigException("dashboards.json has an empty \"pages\" list - add at least one page", null)
        }
        return (0 until pages.length()).map { toPage(pages, it) }
    }

    fun toJson(pages: List<Page>): String {
        val entries = JSONArray()
        pages.forEach {
            entries.put(JSONObject().put("url", it.url).put("seconds", it.seconds).put("zoom", it.zoom)
                    .put("renderDelaySeconds", it.renderDelaySeconds).put("reload", it.reload)
            )
        }
        return JSONObject().put("pages", entries).toString(2)
    }

    private fun readPages(json: String): JSONArray =
        try {
            JSONObject(json).getJSONArray("pages")
        } catch (e: JSONException) {
            throw ConfigException("dashboards.json is invalid (${e.message}) - expected {\"pages\":[...]}", e)
        }

    private fun toPage(pages: JSONArray, i: Int): Page =
        try {
            val entry = pages.getJSONObject(i)
            val url = entry.getString("url")
            val seconds = entry.getInt("seconds")
            val zoom = entry.getInt("zoom")
            val renderDelay = entry.getInt("renderDelaySeconds")
            val reload = entry.getBoolean("reload")
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                throw ConfigException("pages[$i].url must start with http:// or https:// but was \"$url\"", null)
            }
            if (seconds < MIN_SECONDS) {
                throw ConfigException("pages[$i].seconds must be >= $MIN_SECONDS but was $seconds", null)
            }
            if (zoom !in MIN_ZOOM..MAX_ZOOM) {
                throw ConfigException("pages[$i].zoom must be $MIN_ZOOM-$MAX_ZOOM (percent) but was $zoom", null)
            }
            if (renderDelay < MIN_RENDER_DELAY) {
                throw ConfigException("pages[$i].renderDelaySeconds must be >= $MIN_RENDER_DELAY but was $renderDelay", null)
            }
            Page(url, seconds, zoom, renderDelay, reload)
        } catch (e: JSONException) {
            throw ConfigException(
                "pages[$i] needs a string \"url\" and int \"seconds\", \"zoom\" and \"renderDelaySeconds\" and boolean \"reload\" (${e.message})", e
            )
        }
}
