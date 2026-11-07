package de.ulf.dashboard

import org.json.JSONObject

data class HttpResponse(val status: Int, val contentType: String, val body: String)

/** HTTP-agnostic request handling for the settings UI. */
class ConfigApi(
    private val store: ConfigStore,
    private val indexHtml: String,
    private val onChange: (List<Page>) -> Unit,
) {
    fun handle(method: String, path: String, body: String): HttpResponse =
        when (path) {
            "/" -> onlyFor(method, "GET") { HttpResponse(200, HTML, indexHtml) }
            "/api/pages" -> pages(method, body)
            else -> error(404, "Unknown path $path")
        }

    private fun pages(method: String, body: String): HttpResponse =
        when (method) {
            "GET" -> json { DashboardConfig.toJson(store.load()) }
            "PUT" -> json { savePages(body) }
            else -> error(405, "Use GET or PUT on /api/pages")
        }

    private fun savePages(body: String): String {
        val pages = DashboardConfig.parse(body) // validate before touching the stored config
        store.save(pages)
        onChange(pages)
        return DashboardConfig.toJson(pages)
    }

    private fun json(produce: () -> String): HttpResponse =
        try {
            HttpResponse(200, JSON, produce())
        } catch (e: ConfigException) {
            error(400, e.message.orEmpty())
        }

    private fun onlyFor(method: String, allowed: String, ok: () -> HttpResponse): HttpResponse =
        if (method == allowed) ok() else error(405, "Use $allowed on this path")

    private fun error(status: Int, message: String): HttpResponse =
        HttpResponse(status, JSON, JSONObject().put("error", message).toString())

    private companion object {
        const val HTML = "text/html; charset=utf-8"
        const val JSON = "application/json; charset=utf-8"
    }
}
