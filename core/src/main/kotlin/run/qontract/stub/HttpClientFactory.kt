package run.qontract.stub

import run.qontract.ignoreLog
import run.qontract.test.HttpClient

class HttpClientFactory {
    fun client(baseURL: String): HttpClient = HttpClient(baseURL, log = ignoreLog)
}
