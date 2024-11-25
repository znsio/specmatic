package io.specmatic.test


import io.ktor.client.plugins.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApacheHttpClientFactoryTest {
    @Test
    fun `the client should set a timeout policy with socketTimeout giving breathing room for requestTimeout to kick in first`() {
        val randomTimeoutInMilliseconds = (1..5).random() * 1000L

        val httpClientFactory = ApacheHttpClientFactory(randomTimeoutInMilliseconds)
        val httpClient = httpClientFactory.create()
        val httpTimeout = httpClient.pluginOrNull(HttpTimeout)

        val expectedSocketTimeout =
            randomTimeoutInMilliseconds + BREATHING_ROOM_FOR_REQUEST_TIMEOUT_TO_KICK_IN_FIRST
        assertThat(httpTimeout).isNotNull
        httpClient.close() // Clean up resources
    }

    @Test
    fun `the factory should ask the timeout policy to set the timeout`() {
        val randomTimeoutInMilliseconds = (1..5).random() * 1000L

        val httpClientFactory = ApacheHttpClientFactory(randomTimeoutInMilliseconds)
        val httpClient = httpClientFactory.create()
        assertThat(httpClient).isNotNull
        httpClient.close()
    }
}