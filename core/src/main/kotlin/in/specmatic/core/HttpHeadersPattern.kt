package `in`.specmatic.core

import `in`.specmatic.core.pattern.*
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value
import io.ktor.http.*

data class HttpHeadersPattern(
    val pattern: Map<String, Pattern> = emptyMap(),
    val ancestorHeaders: Map<String, Pattern>? = null,
    val contentType: String? = null
) {
    init {
        val uniqueHeaders = pattern.keys.map { it.lowercase() }.distinct()
        if (uniqueHeaders.size < pattern.size) {
            throw ContractException("Headers are not unique: ${pattern.keys.joinToString(", ")}")
        }
    }

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

        val keyErrors: List<KeyError> =
            resolver.withUnexpectedKeyCheck(IgnoreUnexpectedKeys).findKeyErrorListCaseInsensitive(
                pattern,
                headersWithRelevantKeys.mapValues { StringValue(it.value) }
            )

        keyErrors.find { it.name == "SOAPAction" }?.apply {
            return MatchFailure(
                this.missingKeyToResult("header", resolver.mismatchMessages).breadCrumb("SOAPAction")
                    .copy(failureReason = FailureReason.SOAPActionMismatch)
            )
        }

        val keyErrorResults: List<Result.Failure> = keyErrors.map {
            it.missingKeyToResult("header", resolver.mismatchMessages).breadCrumb(it.name)
        }

        val lowercasedHeadersWithRelevantKeys = headersWithRelevantKeys.mapKeys { it.key.lowercase() }

        val results: List<Result?> = this.pattern.mapKeys { it.key }.map { (key, pattern) ->
            val keyWithoutOptionality = withoutOptionality(key)
            val sampleValue = lowercasedHeadersWithRelevantKeys[keyWithoutOptionality.lowercase()]

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

        return if (failures.isNotEmpty())
            MatchFailure(Result.Failure.fromFailures(failures))
        else
            MatchSuccess(parameters)
    }

    private fun highlightIfSOAPActionMismatch(missingKey: String): FailureReason? =
        when (withoutOptionality(missingKey)) {
            "SOAPAction" -> FailureReason.SOAPActionMismatch
            else -> null
        }

    private fun withoutIgnorableHeaders(
        headers: Map<String, String>,
        ancestorHeaders: Map<String, Pattern>
    ): Map<String, String> {
        val ancestorHeadersLowerCase = ancestorHeaders.mapKeys { it.key.lowercase() }

        return headers.filterKeys { key ->
            val headerWithoutOptionality = withoutOptionality(key).lowercase()
            ancestorHeadersLowerCase.containsKey(headerWithoutOptionality) || ancestorHeadersLowerCase.containsKey("$headerWithoutOptionality?")
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
        val headers = pattern.mapValues { (key, pattern) ->
            attempt(breadCrumb = "HEADERS.$key") {
                toStringLiteral(resolver.withCyclePrevention(pattern) { it.generate(key, pattern) })
            }
        }.map { (key, value) -> withoutOptionality(key) to value }.toMap()
        if (contentType.isNullOrBlank()) return headers
        return headers.plus(CONTENT_TYPE to contentType)
    }

    private fun toStringLiteral(headerValue: Value) = when (headerValue) {
        is JSONObjectValue -> headerValue.toUnformattedStringLiteral()
        else -> headerValue.toStringLiteral()
    }

    fun generateWithAll(resolver: Resolver): Map<String, String> {
        return attempt(breadCrumb = "HEADERS") {
            pattern.mapValues { (key, pattern) ->
                attempt(breadCrumb = key) {
                    pattern.generateWithAll(resolver).toStringLiteral()
                }
            }
        }.map { (key, value) -> withoutOptionality(key) to value }.toMap()
    }

    fun newBasedOn(row: Row, resolver: Resolver): List<HttpHeadersPattern> {
        return forEachKeyCombinationIn(row.withoutOmittedKeys(pattern, resolver.defaultExampleResolver), row, resolver) { pattern ->
            newBasedOn(pattern, row, resolver)
        }.map { map -> HttpHeadersPattern(map.mapKeys { withoutOptionality(it.key) }, contentType = contentType) }
    }

    fun negativeBasedOn(row: Row, resolver: Resolver) =
        forEachKeyCombinationIn(row.withoutOmittedKeys(pattern, resolver.defaultExampleResolver), row, resolver) { pattern ->
            negativeBasedOn(pattern, row, resolver, true)
        }.map { patternMap ->
            HttpHeadersPattern(
                patternMap.mapKeys { withoutOptionality(it.key) },
                contentType = contentType
            )
        }

    fun newBasedOn(resolver: Resolver): List<HttpHeadersPattern> =
        allOrNothingCombinationIn(pattern) { pattern ->
            newBasedOn(pattern, resolver)
        }.map { patternMap ->
            HttpHeadersPattern(
                patternMap.mapKeys { withoutOptionality(it.key) },
                contentType = contentType
            )
        }

    fun negativeBasedOn(resolver: Resolver): List<HttpHeadersPattern> =
        allOrNothingCombinationIn(pattern) { pattern ->
            negativeBasedOn(pattern, resolver, true)
        }.map { patternMap -> HttpHeadersPattern(patternMap.mapKeys { withoutOptionality(it.key) }) }

    fun encompasses(other: HttpHeadersPattern, thisResolver: Resolver, otherResolver: Resolver): Result {
        val myRequiredKeys = pattern.keys.filter { !isOptional(it) }
        val otherRequiredKeys = other.pattern.keys.filter { !isOptional(it) }

        val missingHeaderResult: Result = checkAllMissingHeaders(myRequiredKeys, otherRequiredKeys, thisResolver)

        val otherWithoutOptionality = other.pattern.mapKeys { withoutOptionality(it.key) }
        val thisWithoutOptionality = pattern.filterKeys { withoutOptionality(it) in otherWithoutOptionality }
            .mapKeys { withoutOptionality(it.key) }

        val valueResults: List<Result> =
            thisWithoutOptionality.keys.map { headerName ->
                thisWithoutOptionality.getValue(headerName).encompasses(
                    resolvedHop(otherWithoutOptionality.getValue(headerName), otherResolver),
                    thisResolver,
                    otherResolver
                ).breadCrumb(headerName)
            }

        val results = listOf(missingHeaderResult).plus(valueResults)

        return Result.fromResults(results).breadCrumb("HEADER")
    }

    private fun checkAllMissingHeaders(
        myRequiredKeys: List<String>,
        otherRequiredKeys: List<String>,
        resolver: Resolver
    ): Result {
        val failures = myRequiredKeys.filter { it !in otherRequiredKeys }.map { missingFixedKey ->
            MissingKeyError(missingFixedKey).missingKeyToResult("header", resolver.mismatchMessages)
                .breadCrumb(missingFixedKey)
        }

        return Result.fromFailures(failures)
    }
}

private fun parseOrString(pattern: Pattern, sampleValue: String, resolver: Resolver) =
    try {
        pattern.parse(sampleValue, resolver)
    } catch (e: Throwable) {
        StringValue(sampleValue)
    }

fun Map<String, String>.withoutDynamicHeaders(): Map<String, String> =
    this.filterKeys { key -> key.lowercase() !in DYNAMIC_HTTP_HEADERS.map { it.lowercase() } }

val DYNAMIC_HTTP_HEADERS = listOf(
    HttpHeaders.Authorization,
    HttpHeaders.UserAgent,
    HttpHeaders.Cookie,
    HttpHeaders.Referrer,
    HttpHeaders.AcceptLanguage,
    HttpHeaders.Host,
    HttpHeaders.IfModifiedSince,
    HttpHeaders.IfNoneMatch,
    HttpHeaders.CacheControl,
    HttpHeaders.ContentLength,
    HttpHeaders.Range,
    HttpHeaders.XForwardedFor,
    HttpHeaders.Date,
    HttpHeaders.Server,
    HttpHeaders.Expires,
    HttpHeaders.LastModified,
    HttpHeaders.ETag,
    HttpHeaders.Vary,
    HttpHeaders.AccessControlAllowCredentials,
    HttpHeaders.AccessControlMaxAge,
    HttpHeaders.AccessControlRequestHeaders,
    HttpHeaders.AccessControlRequestMethod
)