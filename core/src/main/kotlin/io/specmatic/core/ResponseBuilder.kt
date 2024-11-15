package io.specmatic.core

import io.specmatic.core.value.Value
import io.specmatic.stub.RequestContext

class ResponseBuilder(val scenario: Scenario, val serverState: Map<String, Value>) {
    val responseBodyPattern = scenario.httpResponsePattern.body
    val resolver = scenario.resolver

    fun build(requestContext: RequestContext): HttpResponse {
        return scenario.generateHttpResponse(serverState, requestContext)
    }
}