package io.specmatic.core

import io.specmatic.core.pattern.*
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.StringValue
import io.specmatic.stub.softCastValueToXML

const val DEFAULT_RESPONSE_CODE = 1000

data class HttpResponsePattern(
    val headersPattern: HttpHeadersPattern = HttpHeadersPattern(),
    val status: Int = 0,
    val body: Pattern = EmptyStringPattern,
    val responseValueAssertion: ResponseValueAssertion = AnyResponse
) {
    constructor(response: HttpResponse) : this(HttpHeadersPattern(response.headers.mapValues { stringToPattern(it.value, it.key) }), response.status, response.body.exactMatchElseType())

    fun generateResponse(resolver: Resolver): HttpResponse {
        return attempt(breadCrumb = "RESPONSE") {
            val value = softCastValueToXML(resolver.withCyclePrevention(body, body::generate))
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
        val result = _matches(response, resolver)

        return when(result) {
            is Result.Failure -> result.breadCrumb("RESPONSE")
            else -> result
        }
    }

    fun _matches(response: HttpResponse, resolver: Resolver): Result {
        return response to resolver to
            ::matchStatus then
            ::matchHeaders then
            ::matchResponseBodySchema then
            ::matchExactResponseBodyValue then
            ::summarize otherwise
            ::handleError toResult
            ::returnResult
    }

    fun withResponseExampleValue(row: Row, resolver: Resolver): HttpResponsePattern =
        attempt(breadCrumb = "RESPONSE") {
            val responseExample: ResponseExample = row.responseExample ?: return@attempt this

            val responseExampleMatchResult = matches(responseExample.responseExample, resolver)

            if(responseExampleMatchResult is Result.Failure)
                throw ContractException("""Error in response in example "${row.name}": ${responseExampleMatchResult.reportString()}""")

            val expectedResponseValue: HttpResponsePattern =
                HttpResponsePattern(
                    responseExample.headersPattern(),
//                    HttpHeadersPattern(responseExample.responseExample.headers.mapValues { stringToPattern(it.value, it.key) }),
                    responseExample.responseExample.status,
                    responseExample.bodyPattern()
                )

            val responseValueAssertion: ResponseValueAssertion = ValueAssertion(expectedResponseValue)

            this.copy(responseValueAssertion = responseValueAssertion)
        }

    fun matchesMock(response: HttpResponse, resolver: Resolver) = matches(response, resolver)

    private fun matchStatus(parameters: Pair<HttpResponse, Resolver>): MatchingResult<Pair<HttpResponse, Resolver>> {
        if(status == DEFAULT_RESPONSE_CODE)
            return MatchSuccess(parameters)

        val (response, _) = parameters

        val body = response.body

        return when (response.status) {
            status -> MatchSuccess(parameters)
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

    private fun matchResponseBodySchema(parameters: Triple<HttpResponse, Resolver, List<Result.Failure>>): MatchingResult<Triple<HttpResponse, Resolver, List<Result.Failure>>> {
        val (response, resolver, failures) = parameters

        val parsedValue = when (response.body) {
            is StringValue -> try { body.parse(response.body.string, resolver) } catch(e: Throwable) { response.body }
            else -> response.body
        }

        val result = body.matches(parsedValue, resolver)
        if(result is Result.Failure)
            return MatchSuccess(Triple(response, resolver, failures.plus(result.breadCrumb("BODY"))))

        return MatchSuccess(parameters)
    }

    private fun matchExactResponseBodyValue(parameters: Triple<HttpResponse, Resolver, List<Result.Failure>>): MatchingResult<Triple<HttpResponse, Resolver, List<Result.Failure>>> {
        val (response, resolver, failures) = parameters

        val result = responseValueAssertion.matches(response, resolver.copy(mismatchMessages = valueMismatchMessages))

        if(result is Result.Failure)
            return MatchSuccess(Triple(response, resolver, failures.plus(result)))

        return MatchSuccess(parameters)
    }

    fun bodyPattern(newBody: Pattern): HttpResponsePattern = this.copy(body = newBody)

    fun encompasses(other: HttpResponsePattern, olderResolver: Resolver, newerResolver: Resolver): Result {
        if(status != other.status)
            return Result.Failure("The status didn't match", breadCrumb = "STATUS", failureReason = FailureReason.StatusMismatch)

        val headerResult = headersPattern.encompasses(other.headersPattern, Resolver(), Resolver())
        val bodyResult = resolvedHop(body, olderResolver).encompasses(resolvedHop(other.body, newerResolver), olderResolver, newerResolver).breadCrumb("BODY")

        return Result.fromResults(listOf(headerResult, bodyResult)).breadCrumb("RESPONSE")
    }

    fun fromResponseExpectation(response: HttpResponse): HttpResponsePattern {
        val responseHeaders = response.headers.mapValues { stringToPattern(it.value, it.key) }

        val contentTypeHeader = if("content-type" !in responseHeaders.keys.map { it.lowercase() } && headersPattern.contentType != null)
            mapOf("Content-Type" to ExactValuePattern(StringValue(headersPattern.contentType)))
        else
            emptyMap()

        return HttpResponsePattern(
            HttpHeadersPattern(responseHeaders + contentTypeHeader),
            response.status,
            response.body.exactMatchElseType()
        )
    }
}

private val valueMismatchMessages = object : MismatchMessages {
    override fun mismatchMessage(expected: String, actual: String): String {
        return "Value mismatch: Expected $expected, got value $actual"
    }

    override fun unexpectedKey(keyLabel: String, keyName: String): String {
        return "Value mismatch: $keyLabel $keyName in value was unexpected"
    }

    override fun expectedKeyWasMissing(keyLabel: String, keyName: String): String {
        return "Value mismatch: $keyLabel $keyName was missing"
    }

}
