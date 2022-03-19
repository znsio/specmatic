package `in`.specmatic.core

import `in`.specmatic.core.pattern.*
import `in`.specmatic.core.value.StringValue

data class HttpHeadersPattern(
    val pattern: Map<String, Pattern> = emptyMap(),
    val ancestorHeaders: Map<String, Pattern>? = null
) {
    fun matches(headers: Map<String, String>, resolver: Resolver): Result {
        val result = headers to resolver to
                ::matchEach otherwise
                ::handleError toResult
                ::returnResult

        return when (result) {
            is Result.Failure -> result.breadCrumb("HEADERS")
            else -> result
        }
    }

    private fun matchEach(parameters: Pair<Map<String, String>, Resolver>): MatchingResult<Pair<Map<String, String>, Resolver>> {
        val (headers, resolver) = parameters

        val headersWithRelevantKeys = when {
            ancestorHeaders != null -> withoutIgnorableHeaders(headers, ancestorHeaders)
            else -> withoutContentTypeGeneratedByQontract(headers, pattern)
        }

        val keyErrors: List<KeyError> = resolver.withUnexpectedKeyCheck(IgnoreUnexpectedKeys).findKeyErrorList(
            pattern,
            headersWithRelevantKeys.mapValues { StringValue(it.value) }
        )

        keyErrors.find { it.name == "SOAPAction" }?.apply {
            return MatchFailure(this.missingKeyToResult("header", resolver.mismatchMessages).breadCrumb("SOAPAction").copy(failureReason = FailureReason.SOAPActionMismatch))
        }

        val keyErrorResults: List<Result.Failure> = keyErrors.map {
            it.missingKeyToResult("header", resolver.mismatchMessages).breadCrumb(it.name)
        }

        val results: List<Result?> = this.pattern.map { (key, pattern) ->
            val keyWithoutOptionality = withoutOptionality(key)
            val sampleValue = headersWithRelevantKeys[keyWithoutOptionality]

            when {
                sampleValue != null -> {
                    try {
                        val result = resolver.matchesPattern(
                            keyWithoutOptionality,
                            pattern,
                            attempt(breadCrumb = keyWithoutOptionality) {
                                parseOrString(
                                    pattern,
                                    sampleValue,
                                    resolver
                                )
                            })

                        result.breadCrumb(keyWithoutOptionality).failureReason(highlightIfSOAPActionMismatch(key))
                    } catch (e: ContractException) {
                        e.failure().copy(failureReason = highlightIfSOAPActionMismatch(key))
                    } catch (e: Throwable) {
                        Result.Failure(e.localizedMessage, breadCrumb = keyWithoutOptionality)
                            .copy(failureReason = highlightIfSOAPActionMismatch(key))
                    }
                }
                else ->
                    null
            }
        }

        val failures: List<Result.Failure> = keyErrorResults.plus(results.filterIsInstance<Result.Failure>())

        return if(failures.isNotEmpty())
            MatchFailure(Result.Failure.fromFailures(failures))
        else
            MatchSuccess(parameters)
    }

    private fun highlightIfSOAPActionMismatch(missingKey: String): FailureReason? = when (withoutOptionality(missingKey)) {
        "SOAPAction" -> FailureReason.SOAPActionMismatch
        else -> null
    }

    private fun withoutIgnorableHeaders(
        headers: Map<String, String>,
        ancestorHeaders: Map<String, Pattern>
    ): Map<String, String> {
        return headers.filterKeys { key ->
            val headerWithoutOptionality = withoutOptionality(key)
            ancestorHeaders.containsKey(headerWithoutOptionality) || ancestorHeaders.containsKey("$headerWithoutOptionality?")
        }.filterNot { entry ->
            val headerWithoutOptionality = withoutOptionality(entry.key)

            isStandardHeader(headerWithoutOptionality) && "${headerWithoutOptionality}?" in ancestorHeaders
        }
    }

    private fun withoutContentTypeGeneratedByQontract(
        headers: Map<String, String>,
        pattern: Map<String, Pattern>
    ): Map<String, String> {
        val contentTypeHeader = "Content-Type"
        return when {
            contentTypeHeader in headers && contentTypeHeader !in pattern && "$contentTypeHeader?" !in pattern -> headers.minus(
                contentTypeHeader
            )
            else -> headers
        }
    }

    fun generate(resolver: Resolver): Map<String, String> {
        return attempt(breadCrumb = "HEADERS") {
            pattern.mapValues { (key, pattern) ->
                attempt(breadCrumb = key) {
                    resolver.generate(key, pattern).toStringLiteral()
                }
            }
        }.map { (key, value) -> withoutOptionality(key) to value }.toMap()
    }

    fun newBasedOn(row: Row, resolver: Resolver): List<HttpHeadersPattern> =
        forEachKeyCombinationIn(pattern, row) { pattern ->
            newBasedOn(pattern, row, resolver)
        }.map { HttpHeadersPattern(it.mapKeys { withoutOptionality(it.key) }) }

    fun newBasedOn(resolver: Resolver): List<HttpHeadersPattern> =
        allOrNothingCombinationIn(pattern) { pattern ->
            newBasedOn(pattern, resolver)
        }.map { HttpHeadersPattern(it.mapKeys { withoutOptionality(it.key) }) }

    fun encompasses(other: HttpHeadersPattern, thisResolver: Resolver, otherResolver: Resolver): Result {
        val myRequiredKeys = pattern.keys.filter { !isOptional(it) }
        val otherRequiredKeys = other.pattern.keys.filter { !isOptional(it) }

        return checkMissingHeaders(myRequiredKeys, otherRequiredKeys, thisResolver).ifSuccess {
            val otherWithoutOptionality = other.pattern.mapKeys { withoutOptionality(it.key) }
            val thisWithoutOptionality = pattern.filterKeys { withoutOptionality(it) in otherWithoutOptionality }
                .mapKeys { withoutOptionality(it.key) }

            val valueResults =
                thisWithoutOptionality.keys.asSequence().map { key ->
                    Pair(
                        key,
                        thisWithoutOptionality.getValue(key).encompasses(
                            resolvedHop(otherWithoutOptionality.getValue(key), otherResolver),
                            thisResolver,
                            otherResolver
                        )
                    )
                }

            valueResults.find { it.second is Result.Failure }.let { result ->
                result?.second?.breadCrumb(result.first) ?: Result.Success()
            }
        }.breadCrumb("HEADER")
    }

    private fun checkMissingHeaders(myRequiredKeys: List<String>, otherRequiredKeys: List<String>, resolver: Resolver): Result =
        when (val missingFixedKey = myRequiredKeys.find { it !in otherRequiredKeys }) {
            null -> Result.Success()
            else -> MissingKeyError(missingFixedKey).missingKeyToResult("header", resolver.mismatchMessages).breadCrumb(missingFixedKey)
        }
}

private fun parseOrString(pattern: Pattern, sampleValue: String, resolver: Resolver) =
    try {
        pattern.parse(sampleValue, resolver)
    } catch (e: Throwable) {
        StringValue(sampleValue)
    }

private val standardCommonHTTPHeaders = setOf("A-IM", "Accept", "Accept-Charset", "Accept-Datetime", "Accept-Encoding", "Accept-Language", "Access-Control-Request-Method,", "Access-Control-Request-Headers", "Authorization", "Cache-Control", "Connection", "Content-Encoding", "Content-Length", "Content-MD5", "Content-Type", "Cookie", "Date", "Expect", "Forwarded", "From", "Host", "HTTP2-Settings", "If-Match", "If-Modified-Since", "If-None-Match", "If-Range", "If-Unmodified-Since", "Max-Forwards", "Origin", "Pragma", "Prefer", "Proxy-Authorization", "Range", "Referer", "TE", "Trailer", "Transfer-Encoding", "User-Agent", "Upgrade", "Via", "Warning")

fun isStandardHeader(header: String): Boolean = withoutOptionality(header) in standardCommonHTTPHeaders

