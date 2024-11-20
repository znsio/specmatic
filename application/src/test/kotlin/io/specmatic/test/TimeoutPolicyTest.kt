package io.specmatic.test

import io.ktor.client.plugins.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import io.ktor.client.*
import io.ktor.client.engine.cio.*



private const val ONE_SECOND_IN_MILLISECONDS = 1000L

class TimeoutPolicyTest {

    @Test
    fun `timeout policy should set the socket timeout to longer than the request timeout to let the request timeout kick in first` () {

        val timeoutPolicy = TimeoutPolicy(ONE_SECOND_IN_MILLISECONDS)

        assertThat(timeoutPolicy.requestTimeoutInMillis).isEqualTo(ONE_SECOND_IN_MILLISECONDS)
        assertThat(timeoutPolicy.socketTimeoutInMillis).isEqualTo(ONE_SECOND_IN_MILLISECONDS + BREATHING_ROOM_FOR_REQUEST_TIMEOUT_TO_KICK_IN_FIRST)
    }

//    @Test
//    fun `timeout policy should set the socket and request timeouts in the TimeoutPolicy ktor plugin as configured` () {
//        val timeoutPolicy = TimeoutPolicy(ONE_SECOND_IN_MILLISECONDS)
//
//        val client = HttpClient(CIO) {
//            expectSuccess = false
//
//            // Applying the HttpTimeout plugin
//            install(HttpTimeout) {
//                // Applying the TimeoutPolicy to the HttpTimeout plugin
//                timeoutPolicy.configure(this)
//            }
//        }
//        val timeoutConfig = client.plugin(HttpTimeout)
//
//        assertThat(timeoutConfig.requestTimeoutMillis).isEqualTo(ONE_SECOND_IN_MILLISECONDS)
//        assertThat(timeoutConfig.socketTimeoutMillis).isEqualTo(ONE_SECOND_IN_MILLISECONDS + BREATHING_ROOM_FOR_REQUEST_TIMEOUT_TO_KICK_IN_FIRST)
//    }

}