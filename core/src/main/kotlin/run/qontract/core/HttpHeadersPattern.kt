package run.qontract.core

import run.qontract.core.pattern.*
import run.qontract.core.value.StringValue

data class HttpHeadersPattern(val pattern: Map<String, Pattern> = emptyMap(), val ancestorHeaders: Map<String, Pattern>? = null) {
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

        val missingKey = resolver.findMissingKey(pattern, headersWithRelevantKeys.mapValues { StringValue(it.value) }, ignoreUnexpectedKeys)
        if(missingKey != null) {
            return MatchFailure(missingKeyToResult(missingKey, "header"))
        }

        this.pattern.forEach { (key, pattern) ->
            val keyWithoutOptionality = withoutOptionality(key)
            val sampleValue = headersWithRelevantKeys[keyWithoutOptionality]

            when {
                sampleValue != null -> try {
                    val result = resolver.matchesPattern(keyWithoutOptionality, pattern, attempt(breadCrumb = keyWithoutOptionality) { parseOrString(pattern, sampleValue, resolver) } )
                    if (result is Result.Failure) {
                        return MatchFailure(result.breadCrumb(keyWithoutOptionality))
                    }
                } catch(e: ContractException) {
                    return MatchFailure(e.failure())
                } catch(e: Throwable) {
                    return MatchFailure(Result.Failure(e.localizedMessage, breadCrumb = keyWithoutOptionality))
                }
                !key.endsWith("?") ->
                    return MatchFailure(missingKeyToResult(MissingKeyError(key), "header").breadCrumb(key))
            }
        }

        return MatchSuccess(parameters)
    }

    private fun withoutIgnorableHeaders(headers: Map<String, String>, ancestorHeaders: Map<String, Pattern>): Map<String, String> {
        return headers.filterKeys { key ->
            val keyWithoutOptionality = withoutOptionality(key)
            ancestorHeaders.containsKey(keyWithoutOptionality) || ancestorHeaders.containsKey("$keyWithoutOptionality?")
        }
    }

    private fun withoutContentTypeGeneratedByQontract(headers: Map<String, String>, pattern: Map<String, Pattern>): Map<String, String> {
        val contentTypeHeader = "Content-Type"
        return when {
            contentTypeHeader in headers && contentTypeHeader !in pattern && "$contentTypeHeader?" !in pattern -> headers.minus(contentTypeHeader)
            else -> headers
        }
    }

    fun generate(resolver: Resolver): Map<String, String> {
        return attempt(breadCrumb = "HEADERS") {
            pattern.mapValues { (key, pattern) ->
                attempt(breadCrumb = key) {
                    resolver.generate(key, pattern).toStringValue()
                }
            }
        }
    }

    fun newBasedOn(row: Row, resolver: Resolver): List<HttpHeadersPattern> =
            forEachKeyCombinationIn(pattern, row) { pattern ->
            newBasedOn(pattern, row, resolver)
        }.map { HttpHeadersPattern(it.mapKeys { withoutOptionality(it.key) }) }

    fun encompasses(other: HttpHeadersPattern, thisResolver: Resolver, otherResolver: Resolver): Result {
        val myRequiredKeys = pattern.keys.filter { !isOptional(it) }
        val otherRequiredKeys = other.pattern.keys.filter { !isOptional(it) }

        return checkMissingHeaders(myRequiredKeys, otherRequiredKeys).ifSuccess {
            val otherWithoutOptionality = other.pattern.mapKeys { withoutOptionality(it.key) }
            val thisWithoutOptionality = pattern.filterKeys { withoutOptionality(it) in otherWithoutOptionality }.mapKeys { withoutOptionality(it.key) }

            val valueResults =
                    thisWithoutOptionality.keys.asSequence().map { key ->
                        Pair(key, thisWithoutOptionality.getValue(key).encompasses(resolvedHop(otherWithoutOptionality.getValue(key), otherResolver), thisResolver, otherResolver))
                    }

            valueResults.find { it.second is Result.Failure }.let { result ->
                result?.second?.breadCrumb(result.first) ?: Result.Success()
            }
        }.breadCrumb("HEADER")
    }

    private fun checkMissingHeaders(myRequiredKeys: List<String>, otherRequiredKeys: List<String>): Result =
            when(val missingFixedKey = myRequiredKeys.find { it !in otherRequiredKeys }) {
                null -> Result.Success()
                else -> missingKeyToResult(MissingKeyError(missingFixedKey), "header").breadCrumb(missingFixedKey)
            }
}

private fun parseOrString(pattern: Pattern, sampleValue: String, resolver: Resolver) =
        try {
            pattern.parse(sampleValue, resolver)
        } catch (e: Throwable) {
            StringValue(sampleValue)
        }
