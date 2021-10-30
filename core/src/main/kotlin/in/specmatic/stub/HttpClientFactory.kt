package `in`.specmatic.stub

import `in`.specmatic.core.log.ignoreLog
import `in`.specmatic.test.HttpClient

class HttpClientFactory {
    fun client(baseURL: String): HttpClient = HttpClient(baseURL, log = ignoreLog)
}
