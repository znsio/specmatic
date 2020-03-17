package run.qontract.core

import run.qontract.core.pattern.*

class HttpResponsePattern : Cloneable {
    var headersPattern: HttpHeadersPattern = HttpHeadersPattern()

    @JvmField
    var status: Int?
    private var body: Pattern = NoContentPattern()

    constructor() {
        status = null
    }

    constructor(response: HttpResponse) {
        status = response.status
        headersPattern.addAll(response.headers)
        body = parsedPattern(response.body!!)
    }

    fun setBodyPattern(bodyContent: String?) {
        body = parsedPattern(bodyContent!!)
    }

    fun generateResponse(resolver: Resolver): HttpResponse {
        val value = body.generate(resolver)
        val headers = headersPattern.generate(resolver)
        headers["Content-Type"] = value.httpContentType
        return HttpResponse(status!!, value.toString(), headers)
    }

    fun matches(response: HttpResponse, resolver: Resolver) =
            response to resolver to
                    ::matchStatus then
                    ::matchHeaders then
                    ::matchBody otherwise
                    ::handleError toResult
                    ::returnResult

    fun newBasedOn(row: Row, resolver: Resolver): HttpResponsePattern {
        val newResponse = clone() as HttpResponsePattern
        newResponse.body = newResponse.body.newBasedOn(row, resolver.copy())
        newResponse.headersPattern = this.headersPattern.newBasedOn(row)
        return newResponse
    }

    fun matchesMock(response: HttpResponse, resolver: Resolver) =
            matches(response, resolver.copy()
                    .also {
                        it.matchPattern = true
                        it.addCustomPattern("(number)", NumericStringPattern())
                    })

    private fun matchStatus(parameters: Pair<HttpResponse, Resolver>): MatchingResult<Pair<HttpResponse, Resolver>> {
        val (response, _) = parameters
        when (response.status != status) {
            true -> return MatchFailure(Result.Failure("    Expected: $status Actual: ${response.status}"))
        }
        return MatchSuccess(parameters)
    }

    private fun matchHeaders(parameters: Pair<HttpResponse, Resolver>): MatchingResult<Pair<HttpResponse, Resolver>> {
        val (response, resolver) = parameters
        when (val result = headersPattern.matches(response.headers, resolver)) {
            is Result.Failure -> return MatchFailure(result.add("Response headers did not match"))
        }
        return MatchSuccess(parameters)
    }

    private fun matchBody(parameters: Pair<HttpResponse, Resolver>): MatchingResult<Pair<HttpResponse, Resolver>> {
        val (response, resolver) = parameters
        val resolverWithNumericString = resolver.copy()
        resolverWithNumericString.addCustomPattern("(number)", NumericStringPattern())
        when (val result = body.matches(parsedValue(response.body), resolverWithNumericString)) {
            is Result.Failure -> return MatchFailure(result.add("Response body did not match"))
        }
        return MatchSuccess(parameters)
    }

    fun deepCopy(): HttpResponsePattern {
        return newBasedOn(Row(), Resolver())
    }
}