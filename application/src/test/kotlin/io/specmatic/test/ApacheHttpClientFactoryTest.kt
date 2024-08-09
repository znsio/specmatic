package io.specmatic.test

import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.random.Random


class ApacheHttpClientFactoryTest {
    @Test
    fun `the client should set a timeout policy with socketTimeout giving breathing room for requestTimeout to kick in first`() {
        val randomTimeoutInMilliseconds = Random.nextInt(1, 6) * 1000L

        val httpClientFactory: HttpClientFactory = ApacheHttpClientFactory(randomTimeoutInMilliseconds)
        val timeoutPolicyFromHttpClientFactory = httpClientFactory.timeoutPolicy

        val expectedSocketTimeout = randomTimeoutInMilliseconds + BREATHING_ROOM_FOR_REQUEST_TIMEOUT_TO_KICK_IN_FIRST

        assertThat(timeoutPolicyFromHttpClientFactory.requestTimeoutInMillis).isEqualTo(randomTimeoutInMilliseconds)
        assertThat(timeoutPolicyFromHttpClientFactory.socketTimeoutInMillis).isEqualTo(expectedSocketTimeout)
    }

    @Test
    fun `the factory should ask the timeout policy to set the timeout`() {
        val timeoutPolicy = mockk<TimeoutPolicy>()

        justRun { timeoutPolicy.configure(any()) }

        ApacheHttpClientFactory(timeoutPolicy).create().close()

        verify(exactly = 1) { timeoutPolicy.configure(any()) }
    }
}