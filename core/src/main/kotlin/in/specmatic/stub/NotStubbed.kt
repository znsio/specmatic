package `in`.specmatic.stub

import `in`.specmatic.core.HttpRequest
import `in`.specmatic.stub.report.StubEndpoint

class NotStubbed(override val response: HttpStubResponse) : StubbedResponseResult {
    override fun log(logs: MutableList<StubEndpoint>, httpRequest: HttpRequest) {

    }
}