package de.ulf.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ShowRequestTest {
    @Test
    fun urlIsEnoughAndOthersGetDefaults() {
        assertEquals(ShowRequest("https://a", 100, true), ShowRequest.parse("{\"url\":\"https://a\"}"))
    }

    @Test
    fun readsZoomAndDesktop() {
        assertEquals(
            ShowRequest("http://a", 250, false),
            ShowRequest.parse("{\"url\":\"http://a\",\"zoom\":250,\"desktop\":false}")
        )
    }

    @Test
    fun rejectsInvalidInput() {
        listOf(
            "nope",
            "{}",
            "{\"url\":\"ftp://a\"}",
            "{\"url\":\"file:///etc/passwd\"}",
            "{\"url\":\"javascript:alert(1)\"}",
            "{\"url\":\"https://a\",\"zoom\":9}",
            "{\"url\":\"https://a\",\"zoom\":501}",
            "{\"url\":\"https://a\",\"zoom\":\"big\"}",
            "{\"url\":\"https://a\",\"desktop\":\"yes\"}",
        ).forEach { body ->
            assertThrows(body, ConfigException::class.java) { ShowRequest.parse(body) }
        }
    }
}
