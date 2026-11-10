package de.ulf.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserAgentTest {
    private val emulator = "Mozilla/5.0 (Linux; Android 11; sdk_gphone_arm64 Build/RSR1.240422.006) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/91.0.4472.114 Safari/537.36"
    private val reducedMobile = "Mozilla/5.0 (Linux; Android 10; K; wv) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Version/4.0 Chrome/139.0.0.0 Mobile Safari/537.36"
    private val desktopPrefix = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "

    @Test
    fun `drops android and webview markers but keeps the chrome version`() {
        assertEquals(desktopPrefix + "Chrome/91.0.4472.114 Safari/537.36", UserAgent.desktop(emulator))
    }

    @Test
    fun `drops mobile and wv from a reduced user agent`() {
        assertEquals(desktopPrefix + "Chrome/139.0.0.0 Safari/537.36", UserAgent.desktop(reducedMobile))
    }

    @Test
    fun `returns null without a chrome token`() {
        assertNull(UserAgent.desktop("Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36"))
    }
}
