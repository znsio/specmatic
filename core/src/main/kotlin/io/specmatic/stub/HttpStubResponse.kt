package io.specmatic.stub

import io.specmatic.core.Feature
import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpResponse
import io.specmatic.core.Scenario
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.Value

data class HttpStubResponse(
    val response: HttpResponse,
    val delayInMilliSeconds: Long? = null,
    val contractPath: String = "",
    val feature: Feature? = null,
    val scenario: Scenario? = null,
    val dictionary: Map<String, Value> = emptyMap()
) {
    fun resolveSubstitutions(
        request: HttpRequest,
        originalRequest: HttpRequest,
        data: JSONObjectValue,
    ): HttpStubResponse {
        if(scenario == null)
            return this

        val updatedResponse = scenario.resolveSubtitutions(request, originalRequest, response, data, dictionary)

        return this.copy(response = updatedResponse)
    }
}