package run.qontract.core

import run.qontract.core.pattern.*
import run.qontract.core.value.StringValue
import run.qontract.stub.softCastValueToXML

data class HttpResponsePattern(val headersPattern: HttpHeadersPattern = HttpHeadersPattern(), val status: Int = 0, val body: Pattern = EmptyStringPattern) {
    constructor(response: HttpResponse) : this(HttpHeadersPattern(response.headers.mapValues { stringToPattern(it.value, it.key) }), response.status, response.body?.exactMatchElseType() ?: EmptyStringPattern)

    fun generateResponse(resolver: Resolver): HttpResponse {
        return attempt(breadCrumb = "RESPONSE") {
            val value = softCastValueToXML(body.generate(resolver))
            val headers = headersPattern.generate(resolver).plus("X-Qontract-Result" to "success").let { headers ->
                when {
                    !headers.containsKey("Content-Type") -> headers.plus("Content-Type" to value.httpContentType)
                    else -> headers
                }
            }
            HttpResponse(status, headers, value)
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

    fun matchesMock(response: HttpResponse, resolver: Resolver) = matches(response, resolver)

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

        val parsedValue = when (response.body) {
            is StringValue -> try { body.parse(response.body.string, resolver) } catch(e: Throwable) { response.body }
            else -> response.body
        }

        when (val result = body.matches(parsedValue, resolver)) {
            is Result.Failure -> return MatchFailure(result.breadCrumb("BODY"))
        }
        return MatchSuccess(parameters)
    }

    fun bodyPattern(newBody: Pattern): HttpResponsePattern = this.copy(body = newBody)
    fun generateResponses(resolver: Resolver): List<HttpResponse> {
        return attempt(breadCrumb = "RESPONSE") {
            val allPossibleBodies = body.newBasedOn(Row(), resolver)
            val allPossibleHeaders = headersPattern.newBasedOn(Row(), resolver)

            allPossibleBodies.flatMap { oneBody ->
                allPossibleHeaders.map { oneHeadersPattern ->
                    val body = oneBody.generate(resolver)
                    val headers = oneHeadersPattern.generate(resolver).plus("Content-Type" to body.httpContentType).plus("X-Qontract-Result" to "success")
                    HttpResponse(status, headers, body)
                }
            }
        }
    }

    fun encompasses(other: HttpResponsePattern, olderResolver: Resolver, newerResolver: Resolver): Result {
        val result = listOf(
                {
                    when {
                        status != other.status -> Result.Failure("The status didn't match", breadCrumb = "STATUS")
                        else -> Result.Success()
                    }
                },
                { headersPattern.encompasses(other.headersPattern, Resolver(), Resolver()) },
                { resolvedHop(body, olderResolver).encompasses(resolvedHop(other.body, newerResolver), olderResolver, newerResolver) }
        ).asSequence().map { it.invoke() }.firstOrNull { it is Result.Failure } ?: Result.Success()

        return result.breadCrumb("RESPONSE")
    }
}
