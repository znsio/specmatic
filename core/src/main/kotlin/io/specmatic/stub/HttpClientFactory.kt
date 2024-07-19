package io.specmatic.stub

import io.specmatic.core.log.ignoreLog
import io.specmatic.test.HttpClient

class HttpClientFactory {
    fun client(baseURL: String): HttpClient = HttpClient(baseURL, log = ignoreLog)
}
