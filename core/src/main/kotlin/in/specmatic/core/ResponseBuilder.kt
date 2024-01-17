package `in`.specmatic.core

import `in`.specmatic.core.value.Value

class ResponseBuilder(val scenario: Scenario, val serverState: Map<String, Value>) {
    fun build(): HttpResponse {
        return scenario.generateHttpResponse(serverState)
    }
}