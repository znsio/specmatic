package io.specmatic.test

import io.ktor.client.plugins.*

open class TimeoutPolicy(timeout: Int) {
    val requestTimeoutInMillis: Long = secondsToMillis(timeout)
    val socketTimeoutInMillis: Long = secondsToMillis(timeout + BREATHING_ROOM_FOR_REQUEST_TIMEOUT_TO_KICK_IN_FIRST)

    fun configure(httpTimeoutCapabilityConfiguration: HttpTimeout.HttpTimeoutCapabilityConfiguration) {
        httpTimeoutCapabilityConfiguration.socketTimeoutMillis = socketTimeoutInMillis
        httpTimeoutCapabilityConfiguration.requestTimeoutMillis = requestTimeoutInMillis
    }
}

fun secondsToMillis(seconds: Int): Long = seconds * 1000L
