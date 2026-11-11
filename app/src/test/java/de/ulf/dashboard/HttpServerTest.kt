package de.ulf.dashboard

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.ServerSocket
import java.net.Socket

class HttpServerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val seed = """{"pages":[{"url":"https://seed","seconds":10,"zoom":100,"renderDelaySeconds":0,"reload":true}]}"""
    private val changes = mutableListOf<List<Page>>()
    private var port = 0
    private lateinit var server: HttpServer

    @Before
    fun setUp() {
        port = ServerSocket(0).use { it.localPort } // free port
        val operations = object : DashboardOperations {
            override fun statusJson(): String = "{}"
            override fun control(action: ControlAction) {}
            override fun show(request: ShowRequest) {}
            override fun endShow() {}
        }
        val api = ConfigApi(ConfigStore(File(tmp.root, "d.json"), seed), "<html>ui</html>", operations) { changes += it }
        server = HttpServer(port, api, { PASSWORD }) { }
        server.start()
    }

    @After
    fun tearDown() = server.stop()

    // "admin:pw" in base64; added to every request except in the auth tests
    private val auth = "Authorization: Basic " + java.util.Base64.getEncoder().encodeToString("admin:$PASSWORD".toByteArray()) + "\r\n"

    private fun raw(request: String): String = rawWithoutAuth(request.replaceFirst("\r\n", "\r\n$auth"))

    private fun rawWithoutAuth(request: String): String =
        Socket("127.0.0.1", port).use {
            it.getOutputStream().apply { write(request.toByteArray()); flush() }
            it.getInputStream().readBytes().toString(Charsets.UTF_8)
        }

    @Test
    fun requestWithoutCredentialsGets401WithChallenge() {
        val response = rawWithoutAuth("GET / HTTP/1.1\r\nHost: x\r\n\r\n")
        assertTrue(response.startsWith("HTTP/1.1 401 Unauthorized"))
        assertTrue(response.contains("WWW-Authenticate: Basic realm=\"Dashboard\""))
        assertTrue(!response.contains("<html>ui</html>"))
    }

    @Test
    fun wrongPasswordGets401() {
        val wrong = "Authorization: Basic " + java.util.Base64.getEncoder().encodeToString("admin:nope".toByteArray())
        assertTrue(rawWithoutAuth("GET / HTTP/1.1\r\n$wrong\r\n\r\n").startsWith("HTTP/1.1 401"))
    }

    @Test
    fun unauthorizedPutDoesNotChangeConfig() {
        val body = """{"pages":[{"url":"https://evil","seconds":5,"zoom":90,"renderDelaySeconds":0,"reload":true}]}"""
        rawWithoutAuth("PUT /api/pages HTTP/1.1\r\nContent-Length: ${body.length}\r\n\r\n$body")
        assertTrue(changes.isEmpty())
    }

    @Test
    fun servesGet() {
        val response = raw("GET / HTTP/1.1\r\nHost: x\r\n\r\n")
        assertTrue(response.startsWith("HTTP/1.1 200 OK"))
        assertTrue(response.endsWith("<html>ui</html>"))
    }

    @Test
    fun putBodyReachesApiIncludingUtf8() {
        val body = """{"pages":[{"url":"https://exämple.org","seconds":5,"zoom":90,"renderDelaySeconds":0,"reload":true}]}"""
        val length = body.toByteArray().size
        val response = raw("PUT /api/pages HTTP/1.1\r\nContent-Length: $length\r\n\r\n$body")
        assertTrue(response.startsWith("HTTP/1.1 200 OK"))
        assertEquals(listOf(listOf(Page("https://exämple.org", 5, 90, 0, true))), changes)
    }

    @Test
    fun malformedRequestGets400() {
        assertTrue(raw("garbage\r\n\r\n").startsWith("HTTP/1.1 400"))
    }

    @Test
    fun oversizedBodyGets400() {
        assertTrue(raw("PUT /api/pages HTTP/1.1\r\nContent-Length: 99999999\r\n\r\n").startsWith("HTTP/1.1 400"))
    }

    private companion object {
        const val PASSWORD = "test-pw"
    }
}
