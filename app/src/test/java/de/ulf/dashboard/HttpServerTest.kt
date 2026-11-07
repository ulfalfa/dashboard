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

    private val seed = """{"pages":[{"url":"https://seed","seconds":10,"zoom":100,"renderDelaySeconds":0}]}"""
    private val changes = mutableListOf<List<Page>>()
    private var port = 0
    private lateinit var server: HttpServer

    @Before
    fun setUp() {
        port = ServerSocket(0).use { it.localPort } // free port
        val api = ConfigApi(ConfigStore(File(tmp.root, "d.json"), seed), "<html>ui</html>") { changes += it }
        server = HttpServer(port, api) { }
        server.start()
    }

    @After
    fun tearDown() = server.stop()

    private fun raw(request: String): String =
        Socket("127.0.0.1", port).use {
            it.getOutputStream().apply { write(request.toByteArray()); flush() }
            it.getInputStream().readBytes().toString(Charsets.UTF_8)
        }

    @Test
    fun servesGet() {
        val response = raw("GET / HTTP/1.1\r\nHost: x\r\n\r\n")
        assertTrue(response.startsWith("HTTP/1.1 200 OK"))
        assertTrue(response.endsWith("<html>ui</html>"))
    }

    @Test
    fun putBodyReachesApiIncludingUtf8() {
        val body = """{"pages":[{"url":"https://exämple.org","seconds":5,"zoom":90,"renderDelaySeconds":0}]}"""
        val length = body.toByteArray().size
        val response = raw("PUT /api/pages HTTP/1.1\r\nContent-Length: $length\r\n\r\n$body")
        assertTrue(response.startsWith("HTTP/1.1 200 OK"))
        assertEquals(listOf(listOf(Page("https://exämple.org", 5, 90, 0))), changes)
    }

    @Test
    fun malformedRequestGets400() {
        assertTrue(raw("garbage\r\n\r\n").startsWith("HTTP/1.1 400"))
    }

    @Test
    fun oversizedBodyGets400() {
        assertTrue(raw("PUT /api/pages HTTP/1.1\r\nContent-Length: 99999999\r\n\r\n").startsWith("HTTP/1.1 400"))
    }
}
