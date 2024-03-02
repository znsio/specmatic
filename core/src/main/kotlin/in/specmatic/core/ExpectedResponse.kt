package `in`.specmatic.core

data class ExpectedResponse(val expectedExactResponsePattern: HttpResponsePattern) : ResponseValueAssertion {
    override fun matches(response: HttpResponse, resolver: Resolver): Result {
        return expectedExactResponsePattern._matches(response, resolver)
    }
}