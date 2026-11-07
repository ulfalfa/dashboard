package de.ulf.dashboard

import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAddressTest {
    @Test
    fun returnsNullOrDottedIpv4() {
        val ip = LocalAddress.findIpv4()
        assertTrue(ip == null || Regex("""\d{1,3}(\.\d{1,3}){3}""").matches(ip))
    }
}
