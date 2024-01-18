package `in`.specmatic.core

import `in`.specmatic.core.pattern.*
import `in`.specmatic.core.utilities.URIUtils
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.StringValue
import java.net.URI

data class HttpQueryParamPattern(val queryPatternPairs: List<Pair<String, Pattern>>) {
    constructor(queryPatterns: Map<String, Pattern> ) : this(queryPatterns.toList())
    val queryPatterns = queryPatternPairs.toMap()

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
            }
        }
    }

    fun newBasedOn(
        row: Row,
        resolver: Resolver
    ): List<Map<String, Pattern>> {
        val newQueryParamsList = attempt(breadCrumb = QUERY_PARAMS_BREADCRUMB) {
            val optionalQueryParams = queryPatterns

            forEachKeyCombinationIn(row.withoutOmittedKeys(optionalQueryParams), row) { entry ->
                newBasedOn(entry.mapKeys { withoutOptionality(it.key) }, row, resolver)
            }
        }
        return newQueryParamsList
    }

    fun matches(httpRequest: HttpRequest, resolver: Resolver): Result {
        val keyErrors =
            resolver.findKeyErrorList(queryPatternPairs.toMap(), httpRequest.queryParams.asMap().mapValues { StringValue(it.value) })
        val keyErrorList: List<Result.Failure> = keyErrors.map {
            it.missingKeyToResult("query param", resolver.mismatchMessages).breadCrumb(it.name)
        }

        val groupedPatternPairs = queryPatternPairs.groupBy { it.first }

        val resultsForGroups:List<Result?> = groupedPatternPairs.map { (key, pairs) ->
            // 1. key is optional and request does not have the key as well
            // 2. key is mandatory and request does not have the key as well -> Result.Failure
            // 3. key in request but not in groupedPatternPairs -> Result.Failure
            // 4. key in request
                // A. key value pattern is an array
                // B. key value pattern is a scalar (not an array)
                // C. multiple pattern pairs with the same key



            Result.Success()
        }

        val results: List<Result?> = queryPatternPairs.map { (key, patternValue) ->
            val keyName = key.removeSuffix("?")

            if (!httpRequest.queryParams.containsKey(keyName)) {
               null
            } else {
                try {
                    val sampleValues: List<String> = httpRequest.queryParams.getValues(keyName)
                    if (patternValue is QueryParameterArrayPattern) {
                        val parsedValue = JSONArrayValue(sampleValues.map { StringValue(it) })
                        resolver.matchesPattern(keyName, patternValue, parsedValue).breadCrumb(keyName)
                    }
                    else {
                        if(sampleValues.count() > 1) {
                            return Result.Failure("Multiple values: $sampleValues found for query parameter: $key. Expected a single value")
                        }
                        val parsedValue = try {
                            patternValue.parse(sampleValues.single(), resolver)
                        } catch (e: Exception) {
                            StringValue(sampleValues.single())
                        }
                        resolver.matchesPattern(keyName, patternValue, parsedValue).breadCrumb(keyName)
                    }
                } catch (e: ContractException) {
                    e.failure().breadCrumb(keyName)
                } catch (e: Throwable) {
                    Result.Failure(e.localizedMessage).breadCrumb(keyName)
                }
            }
        }

        val failures = keyErrorList.plus(results).filterIsInstance<Result.Failure>()
        return if (failures.isNotEmpty())
            Result.Failure.fromFailures(failures).breadCrumb(QUERY_PARAMS_BREADCRUMB)
        else
            Result.Success()
    }

    fun newBasedOn(resolver: Resolver): List<Map<String, Pattern>> {
        return attempt(breadCrumb = QUERY_PARAMS_BREADCRUMB) {
            val optionalQueryParams = queryPatterns

            allOrNothingCombinationIn(optionalQueryParams) { entry ->
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

    fun negativeBasedOn(row: Row, resolver: Resolver): List<Map<String, Pattern>> {
        return attempt(breadCrumb = QUERY_PARAMS_BREADCRUMB) {
            val optionalQueryParams = queryPatterns

            forEachKeyCombinationIn(row.withoutOmittedKeys(optionalQueryParams), row) { entry ->
                negativeBasedOn(entry.mapKeys { withoutOptionality(it.key) }, row, resolver, true)
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
            DeferredPattern(it.value, it.key)
        else
            ExactValuePattern(StringValue(it.value))
    }.let { queryParams ->
        apiKeyQueryParams.associate { apiKeyQueryParam ->
            Pair("${apiKeyQueryParam}?", StringPattern())
        }.plus(queryParams)
    }
    return HttpQueryParamPattern(queryPattern)
}

const val QUERY_PARAMS_BREADCRUMB = "QUERY-PARAMS"