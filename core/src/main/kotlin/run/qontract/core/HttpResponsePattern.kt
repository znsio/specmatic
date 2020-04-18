package run.qontract.core

import run.qontract.core.pattern.*

data class HttpResponsePattern(var headersPattern: HttpHeadersPattern = HttpHeadersPattern(), var status: Int? = null, private var body: Pattern = NoContentPattern()) : Cloneable {
    constructor(response: HttpResponse) : this(HttpHeadersPattern(response.headers.mapValues { stringToPattern(it.value, it.key) }), response.status, parsedPattern(response.body!!))

    fun bodyPattern(bodyContent: String?) = this.copy(body = parsedPattern(bodyContent!!))

    fun setBodyPattern(bodyContent: String?) {
        body = parsedPattern(bodyContent!!)
    }

    fun generateResponse(resolver: Resolver): HttpResponse {
        return attempt(breadCrumb = "RESPONSE") {
            val value = body.generate(resolver)
            val headers = headersPattern.generate(resolver).plus("Content-Type" to value.httpContentType).plus("X-Qontract-Result" to "success")
            HttpResponse(status!!, value.toString(), headers)
        }
    }

    fun matches(response: HttpResponse, resolver: Resolver): Result {
        val result = response to resolver to
                ::matchStatus then
                ::matchHeaders then
                ::matchBody otherwise
                ::handleError toResult
                ::returnResult

        return when(result) {
            is Result.Failure -> result.breadCrumb("RESPONSE")
            else -> result
        }
    }

    fun newBasedOn(row: Row, resolver: Resolver): List<HttpResponsePattern> =
        attempt(breadCrumb = "RESPONSE") {
            body.newBasedOn(row, resolver).flatMap { newBody ->
                headersPattern.newBasedOn(row, resolver).map { newHeadersPattern ->
                    HttpResponsePattern(newHeadersPattern, status, newBody)
                }
            }
        }

    fun matchesMock(response: HttpResponse, resolver: Resolver) =
            matches(response, resolver.copy(matchPatternInValue = true, patterns = resolver.patterns.plus(mapOf("(number)" to NumericStringPattern()))))

    private fun matchStatus(parameters: Pair<HttpResponse, Resolver>): MatchingResult<Pair<HttpResponse, Resolver>> {
        val (response, _) = parameters
        when (response.status != status) {
            true -> return MatchFailure(Result.Failure(message = "Expected status: $status, actual: ${response.status}", breadCrumb = "STATUS"))
        }
        return MatchSuccess(parameters)
    }

    private fun matchHeaders(parameters: Pair<HttpResponse, Resolver>): MatchingResult<Pair<HttpResponse, Resolver>> {
        val (response, resolver) = parameters
        when (val result = headersPattern.matches(response.headers, resolver)) {
            is Result.Failure -> return MatchFailure(result)
        }
        return MatchSuccess(parameters)
    }

    private fun matchBody(parameters: Pair<HttpResponse, Resolver>): MatchingResult<Pair<HttpResponse, Resolver>> {
        val (response, resolver) = parameters
        val resolverWithNumericString = resolver.copy(patterns = resolver.patterns.plus("(number)" to NumericStringPattern()))
        when (val result = body.matches(parsedValue(response.body), resolverWithNumericString)) {
            is Result.Failure -> return MatchFailure(result.breadCrumb("BODY"))
        }
        return MatchSuccess(parameters)
    }

    fun bodyPattern(newBody: Pattern): HttpResponsePattern = this.copy(body = newBody)
}