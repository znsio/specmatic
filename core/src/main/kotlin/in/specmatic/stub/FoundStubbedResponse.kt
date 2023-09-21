package `in`.specmatic.stub

import `in`.specmatic.core.HttpRequest
import `in`.specmatic.stub.report.StubEndpoint

class FoundStubbedResponse(override val response: HttpStubResponse) : StubbedResponseResult {
    override fun log(logs: MutableList<StubEndpoint>, httpRequest: HttpRequest) {
        logs.add(
            StubEndpoint(
                response.scenario?.path,
                httpRequest.method,
                response.response.status,
                response.feature?.sourceProvider,
                response.feature?.sourceRepository,
                response.feature?.sourceRepositoryBranch,
                response.feature?.specification,
                response.feature?.serviceType,
            )
        )
    }
}