package de.ulf.dashboard

import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException

object LocalAddress {
    /** First site-local IPv4 address of an active non-loopback interface; null if none or interfaces are unreadable. */
    fun findIpv4(): String? =
        try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        } catch (e: SocketException) {
            null // display-only information; the caller shows "unknown" instead
        }
}
