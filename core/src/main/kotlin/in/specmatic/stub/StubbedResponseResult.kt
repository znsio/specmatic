package `in`.specmatic.stub

import `in`.specmatic.core.HttpRequest
import `in`.specmatic.stub.report.StubEndpoint

interface StubbedResponseResult {
    fun log(logs: MutableList<StubEndpoint>, httpRequest: HttpRequest)

    val response: HttpStubResponse
}