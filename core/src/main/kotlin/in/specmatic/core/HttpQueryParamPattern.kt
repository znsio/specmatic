package `in`.specmatic.core

import `in`.specmatic.core.pattern.*
import `in`.specmatic.core.utilities.URIUtils
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.StringValue
import java.net.URI

data class HttpQueryParamPattern(val queryPatterns: Map<String, Pattern>, val additionalProperties: Pattern? = null) {

    fun generate(resolver: Resolver): List<Pair<String, String>> {
        return attempt(breadCrumb = "QUERY-PARAMS") {
            queryPatterns.map { it.key.removeSuffix("?") to it.value }.flatMap { (parameterName, pattern) ->
                attempt(breadCrumb = parameterName) {
                    val generatedValue =  resolver.withCyclePrevention(pattern) { it.generate(parameterName, pattern) }
                    if(generatedValue is JSONArrayValue) {
                        generatedValue.list.map { parameterName to it.toString() }
                    }
                    else {
                        listOf(parameterName to generatedValue.toString())
                    }
                }
            }.let { queryParamPairs ->
                if(additionalProperties == null)
                    queryParamPairs
                else
                    queryParamPairs.plus(randomString(5) to additionalProperties.generate(resolver).toStringLiteral())
            }
        }
    }

    fun newBasedOn(
        row: Row,
        resolver: Resolver
    ): Sequence<Map<String, Pattern>> {
        val newQueryParamsList = attempt(breadCrumb = QUERY_PARAMS_BREADCRUMB) {
            val queryParams = queryPatterns.let {
                if(additionalProperties != null)
                    it.plus(randomString(5) to additionalProperties)
                else
                    it
            }

            forEachKeyCombinationIn(row.withoutOmittedKeys(queryParams, resolver.defaultExampleResolver), row) { entry ->
                newBasedOn(entry.mapKeys { withoutOptionality(it.key) }, row, resolver)
            }
        }
        return newQueryParamsList
    }

    fun matches(httpRequest: HttpRequest, resolver: Resolver): Result {
        val queryParams = if(additionalProperties != null) {
            httpRequest.queryParams.withoutMatching(queryPatterns.keys, additionalProperties, resolver)
        } else {
            httpRequest.queryParams
        }

        val keyErrors =
            resolver.findKeyErrorList(queryPatterns, queryParams.asMap().mapValues { StringValue(it.value) })
        val keyErrorList: List<Result.Failure> = keyErrors.map {
            it.missingKeyToResult("query param", resolver.mismatchMessages).breadCrumb(it.name)
        }

        // 1. key is optional and request does not have the key as well
        // 2. key is mandatory and request does not have the key as well -> Result.Failure
        // 3. key in request but not in groupedPatternPairs -> Result.Failure
        // 4. key in request
        // A. key value pattern is an array
        // B. key value pattern is a scalar (not an array)
        // C. multiple pattern patternPairGroup with the same key


        // We don't need unmatched values when:
        // 1. Running contract tests
        // 2. Backward compatibility
        // 3. Stub
        // A. Setting expectation
        // B. Matching incoming request to a stub without expectations

        // Where we need unmatched values:
        // Matching incoming request to stubbed out API

        val results: List<Result?> = queryPatterns.mapNotNull { (key, parameterPattern) ->
            val requestValues = queryParams.getValues(withoutOptionality(key))

            if (requestValues.isEmpty()) return@mapNotNull null

            val keyWithoutOptionality = withoutOptionality(key)

            val requestValuesList = JSONArrayValue(requestValues.map {
                StringValue(it)
            })

            resolver.matchesPattern(keyWithoutOptionality, parameterPattern, requestValuesList).breadCrumb(keyWithoutOptionality)

        }

        val failures = keyErrorList.plus(results).filterIsInstance<Result.Failure>()

        return if (failures.isNotEmpty())
            Result.Failure.fromFailures(failures).breadCrumb(QUERY_PARAMS_BREADCRUMB)
        else
            Result.Success()
    }

    fun newBasedOn(resolver: Resolver): Sequence<Map<String, Pattern>> {
        return attempt(breadCrumb = QUERY_PARAMS_BREADCRUMB) {
            val queryParams = queryPatterns.let {
                if(additionalProperties != null)
                    it.plus(randomString(5) to additionalProperties)
                else
                    it
            }
            allOrNothingCombinationIn(queryParams) { entry ->
                newBasedOn(entry.mapKeys { withoutOptionality(it.key) }, resolver)
            }
        }
    }

    override fun toString(): String {
        return if (queryPatterns.isNotEmpty()) {
            "?" + queryPatterns.mapKeys { it.key.removeSuffix("?") }.map { (key, value) ->
                "$key=$value"
            }.toList().joinToString(separator = "&")
        } else ""
    }

    fun negativeBasedOn(row: Row, resolver: Resolver): Sequence<Map<String, Pattern>> {
        return attempt(breadCrumb = QUERY_PARAMS_BREADCRUMB) {
            val queryParams = queryPatterns.let {
                if(additionalProperties != null)
                    it.plus(randomString(5) to additionalProperties)
                else
                    it
            }

            forEachKeyCombinationIn(row.withoutOmittedKeys(queryParams, resolver.defaultExampleResolver), row) { entry ->
                NegativeNonStringlyPatterns().negativeBasedOn(entry.mapKeys { withoutOptionality(it.key) }, row, resolver)
            }
        }
    }

    fun matches(uri: URI, queryParams: Map<String, String>, resolver: Resolver = Resolver()): Result {
        return matches(HttpRequest(path = uri.path, queryParametersMap =  queryParams), resolver)
    }
}

internal fun buildQueryPattern(
    urlPattern: URI,
    apiKeyQueryParams: Set<String> = emptySet()
): HttpQueryParamPattern {
    val queryPattern = URIUtils.parseQuery(urlPattern.query).mapKeys {
        "${it.key}?"
    }.mapValues {
        if (isPatternToken(it.value))
            QueryParameterScalarPattern(DeferredPattern(it.value, it.key))
        else
            QueryParameterScalarPattern(ExactValuePattern(StringValue(it.value)))
    }.let { queryParams ->
        apiKeyQueryParams.associate { apiKeyQueryParam ->
            Pair("${apiKeyQueryParam}?", StringPattern())
        }.plus(queryParams)
    }
    return HttpQueryParamPattern(queryPattern)
}

const val QUERY_PARAMS_BREADCRUMB = "QUERY-PARAMS"