package application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.ServerSocket

internal class PortUtilTest {
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
}