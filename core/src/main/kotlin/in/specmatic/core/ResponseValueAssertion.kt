package `in`.specmatic.core

interface ResponseValueAssertion {
    fun matches(response: HttpResponse, resolver: Resolver): Result
}