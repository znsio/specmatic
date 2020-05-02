package run.qontract.core

import run.qontract.core.pattern.*
import run.qontract.core.utilities.URIUtils
import run.qontract.core.value.StringValue
import java.net.URI

data class URLMatcher(val queryPattern: Map<String, Pattern>, val pathPattern: List<URLPathPattern>, val path: String) {
    fun matches(uri: URI, sampleQuery: Map<String, String> = emptyMap(), resolver: Resolver = Resolver()): Result {
        val newResolver = withNumericStringPattern(resolver)

        matchesPath(uri, newResolver).let {
            return when (it) {
                is Result.Success -> {
                    matchesQuery(sampleQuery, newResolver)
                }
                else -> it
            }
        }
    }

    private fun matchesPath(uri: URI, resolver: Resolver): Result {
        val pathParts = uri.path.split("/".toRegex()).filter { it.isNotEmpty() }.toTypedArray()

        if (pathPattern.size != pathParts.size)
            return Result.Failure("Expected $uri (having ${pathParts.size} path segments) to match $path (which has ${pathPattern.size} path segments).", breadCrumb = "PATH")

        pathPattern.zip(pathParts).forEach { (urlPathPattern, token) ->
            when (val result = resolver.matchesPattern(urlPathPattern.key, urlPathPattern.pattern, StringValue(token))) {
                is Result.Failure -> return when(urlPathPattern.key) {
                    null -> result.breadCrumb("PATH ($uri)")
                    else -> result.breadCrumb("PATH ($uri)").breadCrumb(urlPathPattern.key)
                }
            }
        }

        return Result.Success()
    }

    private fun matchesQuery(sampleQuery: Map<String, String>, resolver: Resolver): Result {
        val missingKey = resolver.findMissingKey(queryPattern.mapKeys { "${it.key}?" }, sampleQuery.mapValues { StringValue(it.value) })
        if(missingKey != null)
            return Result.Failure("Key $missingKey was missing in query", null, missingKey)

        for (key in queryPattern.keys) {
            if (sampleQuery.containsKey(key)) {
                val patternValue = queryPattern.getValue(key)
                val sampleValue = sampleQuery.getValue(key)

                when (val result = resolver.matchesPattern(key, patternValue, StringValue(sampleValue))) {
                    is Result.Failure -> return result.breadCrumb("QUERY PARAMS").breadCrumb(key)
                }
            }
        }
        return Result.Success()
    }

    fun generatePath(resolver: Resolver): String {
        return attempt(breadCrumb = "PATH") {
            "/" + pathPattern.mapIndexed { index, urlPathPattern ->
                attempt(breadCrumb = "[$index]") {
                    val key = urlPathPattern.key
                    if (key != null) resolver.generate(key, urlPathPattern.pattern) else urlPathPattern.pattern.generate(resolver)
                }
            }.joinToString("/")
        }
    }

    fun generateQuery(resolver: Resolver): Map<String, String> {
        return attempt(breadCrumb = "QUERY PARAMS") {
            queryPattern.map { (name, pattern) ->
                attempt(breadCrumb = name) { name to resolver.generate(name, pattern).toString() }
            }.toMap()
        }
    }

    fun newBasedOn(row: Row, resolver: Resolver): List<URLMatcher> {
        val newResolver = withNumericStringPattern(resolver)

        val newPathPartsList = newBasedOn(pathPattern.mapIndexed { index, it ->
            val key = it.key

            attempt(breadCrumb = "[$index]") {
                when {
                    key !== null && row.containsField(key) -> {
                        val rowValue = row.getField(key)
                        attempt("Format error in example of \"$key\"") { URLPathPattern(ExactMatchPattern(it.parse(rowValue, newResolver))) }
                    }
                    else -> it
                }
            }
        }, row, newResolver)

        val newURLPathPatternsList = newPathPartsList.map { list -> list.map { it as URLPathPattern } }

        val newQueryParamsList = attempt(breadCrumb = "QUERY PARAMS") {
            val optionalQueryParams = queryPattern.mapKeys { "${it.key}?" }

            multipleValidKeys(optionalQueryParams, row) {
                newBasedOn(it.mapKeys { withoutOptionality(it.key) }, row, newResolver)
            }
        }

        return newURLPathPatternsList.flatMap { newURLPathPatterns ->
            newQueryParamsList.map { newQueryParams ->
                URLMatcher(newQueryParams, newURLPathPatterns, path)
            }
        }
    }

    override fun toString(): String {
        val url = StringBuilder()
        url.append(path)
        if (queryPattern.isNotEmpty()) url.append("?")
        url.append(queryPattern.map { (key, value) ->
            "$key=$value"
        }.toList().joinToString(separator = "&"))
        return url.toString()
    }
}

internal fun toURLPattern(urlPattern: URI): URLMatcher {
    val path = urlPattern.path

    val pathPattern = pathToPattern(urlPattern.rawPath)

    val queryPattern = URIUtils.parseQuery(urlPattern.query).mapValues {
        if(isPatternToken(it.value))
            DeferredPattern(it.value, it.key)
        else
            ExactMatchPattern(StringValue(it.value))
    }

    return URLMatcher(queryPattern = queryPattern, path = path, pathPattern = pathPattern)
}

internal fun pathToPattern(rawPath: String): List<URLPathPattern> =
        rawPath.trim('/').split("/").filter { it.isNotEmpty() }.map { part ->
            when {
                isPatternToken(part) -> {
                    val pieces = withoutPatternDelimiters(part).split(":").map { it.trim() }
                    if (pieces.size != 2) {
                        throw ContractException("In path ${rawPath}, $part must be of the format (param_name:type), e.g. (id:number)")
                    }

                    val (name, type) = pieces

                    URLPathPattern(DeferredPattern(withPatternDelimiters(type)), name)
                }
                else -> URLPathPattern(ExactMatchPattern(StringValue(part)))
            }
        }

