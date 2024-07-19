package io.specmatic.test

import io.ktor.client.HttpClient

interface HttpClientFactory {
    fun create(): HttpClient
    val timeoutPolicy: TimeoutPolicy
}