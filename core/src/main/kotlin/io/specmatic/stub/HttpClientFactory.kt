package io.specmatic.stub

import io.specmatic.core.log.ignoreLog
import io.specmatic.test.LegacyHttpClient

class HttpClientFactory {
    fun client(baseURL: String): LegacyHttpClient = LegacyHttpClient(baseURL, log = ignoreLog)
}
