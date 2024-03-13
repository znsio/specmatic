package `in`.specmatic.core

object AnyResponse : ResponseValueAssertion {
    override fun matches(response: HttpResponse, resolver: Resolver): Result {
        return Result.Success()
    }
}