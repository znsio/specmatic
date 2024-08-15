package io.specmatic.stub

import io.specmatic.core.Feature
import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpResponse
import io.specmatic.core.Scenario

data class HttpStubResponse(
    val response: HttpResponse,
    val delayInMilliSeconds: Long? = null,
    val contractPath: String = "",
    val feature: Feature? = null,
    val scenario: Scenario? = null
) {
    fun resolveSubstitutions(request: HttpRequest): HttpStubResponse {
        val updatedResponse = scenario?.let {
            it.resolveSubtitutions(request, response)
        } ?: response

        return this.copy(response = updatedResponse)
    }
}