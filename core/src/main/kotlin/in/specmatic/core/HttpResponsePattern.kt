package `in`.specmatic.core

import `in`.specmatic.core.pattern.*
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.stub.softCastValueToXML

const val DEFAULT_RESPONSE_CODE = 1000

data class HttpResponsePattern(val headersPattern: HttpHeadersPattern = HttpHeadersPattern(), val status: Int = 0, val body: Pattern = EmptyStringPattern) {
    constructor(response: HttpResponse) : this(HttpHeadersPattern(response.headers.mapValues { stringToPattern(it.value, it.key) }), response.status, response.body.exactMatchElseType())

    fun generateResponse(resolver: Resolver): HttpResponse {
        return attempt(breadCrumb = "RESPONSE") {
            val value = softCastValueToXML(body.generate(resolver))
            val headers = headersPattern.generate(resolver).plus(SPECMATIC_RESULT_HEADER to "success").let { headers ->
                when {
                    !headers.containsKey("Content-Type") -> headers.plus("Content-Type" to value.httpContentType)
                    else -> headers
                }
            }
            HttpResponse(status, headers, value)
        }
    }

    fun generateResponseWithAll(resolver: Resolver): HttpResponse {
        return attempt(breadCrumb = "RESPONSE") {
            val value = softCastValueToXML(body.generateWithAll(resolver))
            val headers = headersPattern.generateWithAll(resolver).plus(SPECMATIC_RESULT_HEADER to "success").let { headers ->
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
                ::matchBody then
                ::summarize otherwise
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
        if(status == DEFAULT_RESPONSE_CODE)
            return MatchSuccess(parameters)

        val (response, _) = parameters

        val body = response.body

        return when (response.status) {
            status -> {
                if(Flags.customResponse() && response.status.toString().startsWith("2") && body is JSONObjectValue && body.findFirstChildByPath("resultStatus.status")?.toStringLiteral() == "FAILED")
                    MatchFailure(mismatchResult("status $status and resultStatus.status == \"SUCCESS\"", "status ${response.status} and resultStatus.status == \"${body.findFirstChildByPath("resultStatus.status")?.toStringLiteral()}\"").copy(breadCrumb = "STATUS", failureReason = FailureReason.StatusMismatch))
                else
                    MatchSuccess(parameters)
            }
            else -> MatchFailure(mismatchResult("status $status", "status ${response.status}").copy(breadCrumb = "STATUS", failureReason = FailureReason.StatusMismatch))
        }
    }

    private fun matchHeaders(parameters: Pair<HttpResponse, Resolver>): MatchingResult<Triple<HttpResponse, Resolver, List<Result.Failure>>> {
        val (response, resolver) = parameters
        return when (val result = headersPattern.matches(response.headers, resolver)) {
            is Result.Failure -> MatchSuccess(Triple(response, resolver, listOf(result)))
            else -> MatchSuccess(Triple(response, resolver, emptyList()))
        }
    }

    private fun matchBody(parameters: Triple<HttpResponse, Resolver, List<Result.Failure>>): MatchingResult<Triple<HttpResponse, Resolver, List<Result.Failure>>> {
        val (response, resolver, failures) = parameters

        val parsedValue = when (response.body) {
            is StringValue -> try { body.parse(response.body.string, resolver) } catch(e: Throwable) { response.body }
            else -> response.body
        }

        return when (val result = body.matches(parsedValue, resolver)) {
            is Result.Failure -> MatchSuccess(Triple(response, resolver, failures.plus(result.breadCrumb("BODY"))))
            else -> MatchSuccess(parameters)
        }
    }

    fun bodyPattern(newBody: Pattern): HttpResponsePattern = this.copy(body = newBody)

    fun encompasses(other: HttpResponsePattern, olderResolver: Resolver, newerResolver: Resolver): Result {
        if(status != other.status)
            return Result.Failure("The status didn't match", breadCrumb = "STATUS", failureReason = FailureReason.StatusMismatch)

        val headerResult = headersPattern.encompasses(other.headersPattern, Resolver(), Resolver())
        val bodyResult = resolvedHop(body, olderResolver).encompasses(resolvedHop(other.body, newerResolver), olderResolver, newerResolver).breadCrumb("BODY")

        return Result.fromResults(listOf(headerResult, bodyResult)).breadCrumb("RESPONSE")
    }
}
