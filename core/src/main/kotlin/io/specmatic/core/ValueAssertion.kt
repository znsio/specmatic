package io.specmatic.core

data class ValueAssertion(val expectedExactResponsePattern: HttpResponsePattern) : ResponseValueAssertion {
    override fun matches(response: HttpResponse, resolver: Resolver): Result {
        return expectedExactResponsePattern.matchesResponse(response, resolver)
    }
}