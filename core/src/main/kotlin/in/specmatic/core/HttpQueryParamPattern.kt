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

        val resultsForGroups: List<Result?> = groupedPatternPairs.map { (key, patternPairGroup) ->
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
            // 1. Matching incoming request to stubbed out API

            val requestValues = httpRequest.queryParams.getValues(withoutOptionality(key))

            val keyWithoutOptionality = withoutOptionality(key)

//            if (requestValues.isEmpty())
//                return@map (
//                    if(isOptional(key))
//                        Result.Success()
//                    else
//                        Result.Failure("Missing query parameter: $keyWithoutOptionality")
//                ).breadCrumb(keyWithoutOptionality)

            if (patternPairGroup.first().second is QueryParameterArrayPattern) {
                val list = JSONArrayValue(requestValues.map { StringValue(it) })
                resolver.matchesPattern(keyWithoutOptionality, patternPairGroup.single().second, list)
            } else {
                val initialRequestValuesBeforeMatching = requestValues.map { Pair(it, emptyList<Result.Failure>()) }
                val (matchResults, unmatchedKeys) =
                    patternPairGroup.foldRight(emptyList<Result>() to initialRequestValuesBeforeMatching) {
                            (_, pattern), (results, unmatchedValuesWithReasons) ->
                        val matchResultsForValuesWithThisKey = unmatchedValuesWithReasons.map { (value, failuresSoFar) ->
                            val parsedValue = try {
                                pattern.parse(value, resolver)
                            } catch (e: Exception) {
                                StringValue(value)
                            }

                            resolver.matchesPattern(keyWithoutOptionality, pattern, parsedValue) to Pair(value, failuresSoFar)
                        }

                        if(matchResultsForValuesWithThisKey.none { it.first is Result.Success }) {
                            results.plus(Result.fromResults(matchResultsForValuesWithThisKey.map { it.first })) to unmatchedValuesWithReasons
                        }
                        else {

                            val valuesMatched =
                                matchResultsForValuesWithThisKey.filter { (result, _) ->
                                    result is Result.Success
                                }.map { (_, parameterMismatches) ->
                                    val (paramName, _) = parameterMismatches
                                    paramName
                                }.toSet()


                            val unmatchedValues = (unmatchedValuesWithReasons.map { it.first } - valuesMatched).toSet()

                            val asYetUnmatchedValuesWithOlderReasons = unmatchedValuesWithReasons.filter {
                                it.first in unmatchedValues
                            }.toMap()

                            val currentParameterMismatches = matchResultsForValuesWithThisKey.filter { (_, parameterMismatches) ->
                                val (paramName, _) = parameterMismatches
                                paramName in unmatchedValues
                            }

                            val parameterMismatchesSoFar = currentParameterMismatches.map { (result, parameterMismatches) ->
                                val (paramName, _) = parameterMismatches
                                Pair(paramName, asYetUnmatchedValuesWithOlderReasons.getValue(paramName).plus(result as Result.Failure))
                            }

                            results.plus(Result.Success()) to parameterMismatchesSoFar
                        }
                }

                val overallMatchResultForTheKey = Result.fromResults(matchResults)

                val unmatchedKeysResult = if(unmatchedKeys.isEmpty()) {
                    Result.Success()
                } else {
                    Result.fromResults(unmatchedKeys.flatMap { it.second })
                }

                Result.fromResults(listOf(overallMatchResultForTheKey, unmatchedKeysResult))
            }.breadCrumb(keyWithoutOptionality)
        }

        val failures = keyErrorList.plus(resultsForGroups).filterIsInstance<Result.Failure>()

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