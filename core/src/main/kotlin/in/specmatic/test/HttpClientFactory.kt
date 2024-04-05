package `in`.specmatic.test

import io.ktor.client.HttpClient

interface HttpClientFactory {
    fun create(): HttpClient
}