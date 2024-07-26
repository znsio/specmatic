package io.specmatic.stub

import io.specmatic.core.HttpRequest
import io.specmatic.stub.report.StubEndpoint

interface StubbedResponseResult {
    fun log(logs: MutableList<StubEndpoint>, httpRequest: HttpRequest)

    val response: HttpStubResponse
}