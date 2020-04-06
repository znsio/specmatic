package run.qontract.core

import run.qontract.core.pattern.*

data class HttpResponsePattern(var headersPattern: HttpHeadersPattern = HttpHeadersPattern(), var status: Int? = null, private var body: Pattern = NoContentPattern()) : Cloneable {
    constructor(response: HttpResponse) : this(HttpHeadersPattern(response.headers), response.status, parsedPattern(response.body!!))

    fun bodyPattern(bodyContent: String?) = this.copy(body = parsedPattern(bodyContent!!))

    fun setBodyPattern(bodyContent: String?) {
        body = parsedPattern(bodyContent!!)
    }

    fun generateResponse(resolver: Resolver): HttpResponse {
        val value = body.generate(resolver)
        val headers = headersPattern.generate(resolver)
        headers["Content-Type"] = value.httpContentType
        return HttpResponse(status!!, value.toString(), headers)
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
        body.newBasedOn(row, resolver.makeCopy()).flatMap { newBody ->
            headersPattern.newBasedOn(row).map { newHeadersPattern ->
                HttpResponsePattern(newHeadersPattern, status, newBody)
            }
        }

    fun matchesMock(response: HttpResponse, resolver: Resolver) =
            matches(response, resolver.makeCopy(true, mapOf("(number)" to NumericStringPattern())))

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
            is Result.Failure -> return MatchFailure(result.breadCrumb("HEADERS"))
        }
        return MatchSuccess(parameters)
    }

    private fun matchBody(parameters: Pair<HttpResponse, Resolver>): MatchingResult<Pair<HttpResponse, Resolver>> {
        val (response, resolver) = parameters
        val resolverWithNumericString = resolver.copy(patterns = resolver.patterns.plus("(number)" to NumericStringPattern()))
        when (val result = body.matches(parsedValue(response.body), resolverWithNumericString)) {
            is Result.Failure -> return MatchFailure(result.breadCrumb("PAYLOAD"))
        }
        return MatchSuccess(parameters)
    }

    fun bodyPattern(newBody: Pattern): HttpResponsePattern = this.copy(body = newBody)
}