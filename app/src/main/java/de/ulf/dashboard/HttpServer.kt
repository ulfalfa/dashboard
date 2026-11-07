package de.ulf.dashboard

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.Executors

/**
 * Minimal blocking HTTP/1.1 server: one request per connection, delegates to [ConfigApi].
 * Every request needs Basic auth with the current [password] (read per request, so changes apply immediately).
 */
class HttpServer(
    private val port: Int,
    private val api: ConfigApi,
    private val password: () -> String,
    private val log: (String) -> Unit,
) {
    private class Request(val method: String, val path: String, val body: String, val authorization: String?)

    private class BadRequest(message: String) : Exception(message)

    private var serverSocket: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool { r -> Thread(r).apply { isDaemon = true } }

    /** @throws IOException if the port cannot be bound (e.g. already in use) */
    fun start() {
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(port))
        serverSocket = socket
        pool.execute { acceptLoop(socket) }
    }

    fun stop() {
        serverSocket?.close()
        pool.shutdownNow()
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (!socket.isClosed) {
            try {
                val client = socket.accept()
                pool.execute { serve(client) }
            } catch (e: SocketException) {
                if (!socket.isClosed) log("accept failed: ${e.message}")
            }
        }
    }

    private fun serve(client: Socket) {
        client.use {
            try {
                it.soTimeout = READ_TIMEOUT_MS
                val response = try {
                    val request = readRequest(DataInputStream(it.getInputStream().buffered()))
                    if (BasicAuth.isAuthorized(request.authorization, password())) {
                        api.handle(request.method, request.path, request.body)
                    } else {
                        HttpResponse(401, "text/plain; charset=utf-8", "Login required (user ${BasicAuth.USER})")
                    }
                } catch (e: BadRequest) {
                    HttpResponse(400, "text/plain; charset=utf-8", e.message.orEmpty())
                }
                write(it, response)
            } catch (e: IOException) {
                log("connection failed: ${e.message}")
            }
        }
    }

    private fun readRequest(input: DataInputStream): Request {
        val requestLine = readLine(input).split(" ")
        if (requestLine.size < 2) throw BadRequest("Malformed request line")
        var contentLength = 0
        var authorization: String? = null
        while (true) {
            val header = readLine(input)
            if (header.isEmpty()) break
            if (header.startsWith("content-length:", ignoreCase = true)) {
                contentLength = header.substringAfter(':').trim().toIntOrNull()
                    ?: throw BadRequest("Invalid Content-Length")
            } else if (header.startsWith("authorization:", ignoreCase = true)) {
                authorization = header.substringAfter(':').trim()
            }
        }
        if (contentLength !in 0..MAX_BODY_BYTES) throw BadRequest("Body must be 0-$MAX_BODY_BYTES bytes")
        val body = ByteArray(contentLength).also { input.readFully(it) }
        return Request(requestLine[0], requestLine[1].substringBefore('?'), body.toString(Charsets.UTF_8), authorization)
    }

    private fun readLine(input: DataInputStream): String {
        val line = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) throw BadRequest("Connection closed mid-request")
            if (b == '\n'.code) break
            if (b != '\r'.code) line.write(b)
            if (line.size() > MAX_LINE_BYTES) throw BadRequest("Header line too long")
        }
        return line.toString(Charsets.ISO_8859_1.name())
    }

    private fun write(client: Socket, response: HttpResponse) {
        val body = response.body.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 ${response.status} ${reason(response.status)}\r\n" +
            "Content-Type: ${response.contentType}\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            (if (response.status == 401) "WWW-Authenticate: Basic realm=\"Dashboard\"\r\n" else "") +
            "Connection: close\r\n\r\n"
        client.getOutputStream().apply {
            write(head.toByteArray(Charsets.ISO_8859_1))
            write(body)
            flush()
        }
    }

    private fun reason(status: Int): String =
        when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            else -> "Error"
        }

    private companion object {
        const val READ_TIMEOUT_MS = 5_000
        const val MAX_BODY_BYTES = 64 * 1024
        const val MAX_LINE_BYTES = 8 * 1024
    }
}
