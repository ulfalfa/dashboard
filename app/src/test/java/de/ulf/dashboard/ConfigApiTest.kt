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

    private val seed = """{"pages":[{"url":"https://seed","seconds":10,"zoom":100,"renderDelaySeconds":0}]}"""
    private val changes = mutableListOf<List<Page>>()

    private fun api(): ConfigApi =
        ConfigApi(ConfigStore(File(tmp.root, "d.json"), seed), "<html>ui</html>") { changes += it }

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
        val body = """{"pages":[{"url":"https://new","seconds":5,"zoom":80,"renderDelaySeconds":0}]}"""
        assertEquals(200, api.handle("PUT", "/api/pages", body).status)
        assertEquals(listOf(listOf(Page("https://new", 5, 80, 0))), changes)
        assertTrue(api.handle("GET", "/api/pages", "").body.contains("https://new"))
    }

    @Test
    fun invalidPutIsRejectedAndKeepsOldConfig() {
        val api = api()
        val r = api.handle("PUT", "/api/pages", """{"pages":[{"url":"nope","seconds":5,"zoom":80,"renderDelaySeconds":0}]}""")
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
}
