package `in`.specmatic.test

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.random.Random


class RealHttpClientFactoryTest {
    @Test
    fun `the client should set a timeout policy with socketTimeout giving breathing room for requestTimeout to kick in first`() {
        val randomTimeoutInSeconds = Random.nextInt(1, 6)

        val timeoutPolicyFromHttpClientFactory = RealHttpClientFactory(randomTimeoutInSeconds).timeoutPolicy

        val expectedRequestTimeout = secondsToMillis(randomTimeoutInSeconds)
        val expectedSocketTimeout =
            secondsToMillis(randomTimeoutInSeconds + BREATHING_ROOM_FOR_REQUEST_TIMEOUT_TO_KICK_IN_FIRST)

        assertThat(timeoutPolicyFromHttpClientFactory.requestTimeoutInMillis).isEqualTo(expectedRequestTimeout)
        assertThat(timeoutPolicyFromHttpClientFactory.socketTimeoutInMillis).isEqualTo(expectedSocketTimeout)
    }

    @Test
    fun `the factory should ask the timeout policy to set the timeout`() {
        val timeoutPolicy = mockk<TimeoutPolicy>()

        justRun { timeoutPolicy.configure(any()) }

        RealHttpClientFactory(timeoutPolicy).create().close()

        verify(exactly = 1) { timeoutPolicy.configure(any()) }
    }
}