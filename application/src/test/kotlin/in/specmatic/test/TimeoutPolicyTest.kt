package `in`.specmatic.test

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import io.ktor.client.plugins.HttpTimeout.HttpTimeoutCapabilityConfiguration

private const val ONE_SECOND = 1

class TimeoutPolicyTest {

    @Test
    fun `timeout policy should set the socket timeout to longer than the request timeout to let the request timeout kick in first` () {

        val timeoutPolicy = TimeoutPolicy(ONE_SECOND)

        assertThat(timeoutPolicy.requestTimeoutInMillis).isEqualTo(secondsToMillis(ONE_SECOND))
        assertThat(timeoutPolicy.socketTimeoutInMillis).isEqualTo(secondsToMillis(ONE_SECOND + BREATHING_ROOM_FOR_REQUEST_TIMEOUT_TO_KICK_IN_FIRST))
    }

    @Test
    fun `timeout policy should set the socket and request timeouts in the TimeoutPolicy ktor plugin as configured` () {
        val timeoutPolicy = TimeoutPolicy(ONE_SECOND)

        val pluginTimeoutConfiguration = HttpTimeoutCapabilityConfiguration()

        timeoutPolicy.configure(pluginTimeoutConfiguration)

        assertThat(pluginTimeoutConfiguration.requestTimeoutMillis).isEqualTo(secondsToMillis(ONE_SECOND))
        assertThat(pluginTimeoutConfiguration.socketTimeoutMillis).isEqualTo(secondsToMillis(ONE_SECOND + BREATHING_ROOM_FOR_REQUEST_TIMEOUT_TO_KICK_IN_FIRST))
    }
}