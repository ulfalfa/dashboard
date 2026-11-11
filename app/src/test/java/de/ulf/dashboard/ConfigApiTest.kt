package de.ulf.dashboard

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ConfigApiTest {
    @get:Rule val tmp = TemporaryFolder()

    private val seed = """{"pages":[{"url":"https://seed","seconds":10,"zoom":100,"renderDelaySeconds":0,"reload":true}]}"""
    private val changes = mutableListOf<List<Page>>()
    private val actions = mutableListOf<ControlAction>()
    private val shown = mutableListOf<ShowRequest>()
    private var ended = 0

    private val operations = object : DashboardOperations {
        override fun statusJson(): String = "{\"paused\":false}"
        override fun control(action: ControlAction) { actions += action }
        override fun show(request: ShowRequest) { shown += request }
        override fun endShow() { ended++ }
    }

    private fun api(): ConfigApi =
        ConfigApi(ConfigStore(File(tmp.root, "d.json"), seed), "<html>ui</html>", operations) { changes += it }

    @Test
    fun servesIndexHtml() {
        val r = api().handle("GET", "/", "")
        assertEquals(200, r.status)
        assertEquals("<html>ui</html>", r.body)
    }

    @Test
    fun getReturnsCurrentPages() {
        val r = api().handle("GET", "/api/pages", "")
        assertEquals(200, r.status)
        assertEquals("https://seed", JSONObject(r.body).getJSONArray("pages").getJSONObject(0).getString("url"))
    }

    @Test
    fun putSavesAndNotifiesListener() {
        val api = api()
        val body = """{"pages":[{"url":"https://new","seconds":5,"zoom":80,"renderDelaySeconds":0,"reload":true}]}"""
        assertEquals(200, api.handle("PUT", "/api/pages", body).status)
        assertEquals(listOf(listOf(Page("https://new", 5, 80, 0, true))), changes)
        assertTrue(api.handle("GET", "/api/pages", "").body.contains("https://new"))
    }

    @Test
    fun invalidPutIsRejectedAndKeepsOldConfig() {
        val api = api()
        val r = api.handle("PUT", "/api/pages", """{"pages":[{"url":"nope","seconds":5,"zoom":80,"renderDelaySeconds":0,"reload":true}]}""")
        assertEquals(400, r.status)
        assertTrue(JSONObject(r.body).getString("error").contains("http"))
        assertTrue(changes.isEmpty())
        assertTrue(api.handle("GET", "/api/pages", "").body.contains("https://seed"))
    }

    @Test
    fun unknownPathAndWrongMethodAreRejected() {
        assertEquals(404, api().handle("GET", "/nope", "").status)
        assertEquals(405, api().handle("DELETE", "/api/pages", "").status)
        assertEquals(405, api().handle("POST", "/", "").status)
    }

    @Test
    fun statusIsServedFromOperations() {
        val r = api().handle("GET", "/api/status", "")
        assertEquals(200, r.status)
        assertEquals("{\"paused\":false}", r.body)
        assertEquals(405, api().handle("POST", "/api/status", "").status)
    }

    @Test
    fun controlRunsKnownActions() {
        val api = api()
        assertEquals(200, api.handle("POST", "/api/control", "{\"action\":\"next\"}").status)
        assertEquals(200, api.handle("POST", "/api/control", "{\"action\":\"prev\"}").status)
        assertEquals(listOf(ControlAction.NEXT, ControlAction.PREVIOUS), actions)
    }

    @Test
    fun showPassesTheRequestToOperationsWithDefaults() {
        val r = api().handle("POST", "/api/show", "{\"url\":\"https://spiegel.de\"}")
        assertEquals(200, r.status)
        assertEquals(listOf(ShowRequest("https://spiegel.de", 100, true)), shown)
    }

    @Test
    fun showRejectsBadRequests() {
        val api = api()
        assertEquals(400, api.handle("POST", "/api/show", "{\"url\":\"javascript:alert(1)\"}").status)
        assertEquals(400, api.handle("POST", "/api/show", "{}").status)
        assertEquals(400, api.handle("POST", "/api/show", "nope").status)
        assertEquals(405, api.handle("GET", "/api/show", "").status)
        assertTrue(shown.isEmpty())
    }

    @Test
    fun deleteEndsTheShownPage() {
        assertEquals(200, api().handle("DELETE", "/api/show", "").status)
        assertEquals(1, ended)
    }

    @Test
    fun controlRejectsUnknownActionsAndBadBodies() {
        val api = api()
        assertEquals(400, api.handle("POST", "/api/control", "{\"action\":\"explode\"}").status)
        assertEquals(400, api.handle("POST", "/api/control", "nope").status)
        assertEquals(400, api.handle("POST", "/api/control", "{}").status)
        assertEquals(405, api.handle("GET", "/api/control", "").status)
        assertTrue(actions.isEmpty())
    }
}
