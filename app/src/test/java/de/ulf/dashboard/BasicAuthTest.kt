package de.ulf.dashboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class BasicAuthTest {
    private fun header(credentials: String): String = "Basic " + Base64.getEncoder().encodeToString(credentials.toByteArray())

    @Test
    fun acceptsCorrectCredentials() {
        assertTrue(BasicAuth.isAuthorized(header("admin:secret"), "secret"))
    }

    @Test
    fun rejectsWrongPasswordUserAndMissingHeader() {
        assertFalse(BasicAuth.isAuthorized(header("admin:wrong"), "secret"))
        assertFalse(BasicAuth.isAuthorized(header("root:secret"), "secret"))
        assertFalse(BasicAuth.isAuthorized(null, "secret"))
    }

    @Test
    fun rejectsOtherSchemesAndBrokenBase64() {
        assertFalse(BasicAuth.isAuthorized("Bearer abc", "secret"))
        assertFalse(BasicAuth.isAuthorized("Basic !!!not-base64", "secret"))
    }

    @Test
    fun passwordMayContainColon() {
        assertTrue(BasicAuth.isAuthorized(header("admin:se:cret"), "se:cret"))
    }
}
