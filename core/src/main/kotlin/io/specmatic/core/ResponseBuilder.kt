package io.specmatic.core

import io.specmatic.core.value.Value
import io.specmatic.stub.RequestContext

class ResponseBuilder(val scenario: Scenario, val serverState: Map<String, Value>) {
    fun build(requestContext: RequestContext): HttpResponse {
        return scenario.generateHttpResponse(serverState, requestContext)
    }
}