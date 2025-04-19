package io.specmatic.core.utilities

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.ServerSocket

class PortUtilTest {
    @Test
    fun `should correctly determine if a port is free`() {
        val host = "0.0.0.0"
        val randomFreePort = findRandomFreePort()
        assertThat(portIsInUse(host, randomFreePort)).isFalse
    }

    @Test
    fun `should correctly determine if a port is in use`() {
        val host = "0.0.0.0"
        val randomFreePort = findRandomFreePort()
        ServerSocket(randomFreePort, 1, InetAddress.getByName(host)).use {
            assertThat(portIsInUse(host, randomFreePort)).isTrue
        }
    }

    @Test
    fun `should return the preferred port as is if it is free`() {
        val host = "0.0.0.0"
        val randomFreePort = findRandomFreePort()
        assertThat(findFreePort(host, randomFreePort)).isEqualTo(randomFreePort)
    }

    @Test
    fun `should return a new free port if the preferred port is in use`() {
        val host = "0.0.0.0"
        val preferredFreePort = findRandomFreePort()
        ServerSocket(preferredFreePort, 1, InetAddress.getByName(host)).use {
            val newFreePort = findFreePort(host, preferredFreePort)
            assertThat(newFreePort).isNotEqualTo(preferredFreePort)
            assertThat(portIsInUse(host, newFreePort)).isFalse()
        }
    }
}