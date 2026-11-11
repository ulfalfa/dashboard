package de.ulf.dashboard

import org.json.JSONException
import org.json.JSONObject

/**
 * A page thrown onto the board ad hoc: shown above the rotation, which stays paused until someone ends it.
 * Body: {"url":"https://...","zoom":100,"desktop":true}; only "url" is required.
 */
data class ShowRequest(val url: String, val zoom: Int, val desktop: Boolean) {
    companion object {
        const val DEFAULT_ZOOM = 100

        fun parse(json: String): ShowRequest =
            try {
                val body = JSONObject(json)
                val url = body.getString("url")
                val zoom = if (body.has("zoom")) body.getInt("zoom") else DEFAULT_ZOOM
                val desktop = if (body.has("desktop")) body.getBoolean("desktop") else true
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    throw ConfigException("url must start with http:// or https:// but was \"$url\"", null)
                }
                if (zoom !in DashboardConfig.MIN_ZOOM..DashboardConfig.MAX_ZOOM) {
                    throw ConfigException(
                        "zoom must be ${DashboardConfig.MIN_ZOOM}-${DashboardConfig.MAX_ZOOM} (percent) but was $zoom", null
                    )
                }
                ShowRequest(url, zoom, desktop)
            } catch (e: JSONException) {
                throw ConfigException("Body must be {\"url\": \"https://...\"} with optional int \"zoom\" and boolean \"desktop\" (${e.message})", e)
            }
    }
}
