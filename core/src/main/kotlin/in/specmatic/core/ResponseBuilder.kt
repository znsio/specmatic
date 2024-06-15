package `in`.specmatic.core

import `in`.specmatic.core.value.Value
import `in`.specmatic.stub.RequestContext

class ResponseBuilder(val scenario: Scenario, val serverState: Map<String, Value>) {
    fun build(requestContext: RequestContext): HttpResponse {
        return scenario.generateHttpResponse(serverState, requestContext)
    }
}