package run.qontract.core

import run.qontract.core.pattern.*
import run.qontract.core.utilities.URIUtils
import run.qontract.core.value.StringValue
import java.net.URI

data class URLPattern(val queryPattern: Map<String, Pattern>, val pathPattern: List<Pattern>, val path: String) {
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
            return Result.Failure("Expected $uri to have ${pathPattern.size} parts, but it has ${pathParts.size} parts.")

        pathPattern.zip(pathParts).forEach { (pattern, token) ->
            val key = when (pattern) {
                is Keyed -> pattern.key
                else -> null
            }

            when (val result = resolver.matchesPattern(key, pattern, StringValue(token))) {
                is Result.Failure -> return result.add("Path part did not match in $uri. Expected: $pattern Actual: $token")
            }
        }

        return Result.Success()
    }

    private fun matchesQuery(sampleQuery: Map<String, String>, resolver: Resolver): Result {
        for (key in queryPattern.keys) {
            if (!sampleQuery.containsKey(key)) {
                return Result.Failure("Query parameter not present. Expected: $key Actual: ")
            }
            val patternValue = queryPattern.getValue(key)
            val sampleValue = sampleQuery.getValue(key)

            when (val result = resolver.matchesPattern(key, patternValue, StringValue(sampleValue))) {
                is Result.Failure -> return result.add("Query parameter did not match")
            }
        }
        return Result.Success()
    }

    fun generatePath(resolver: Resolver): String {
        return "/" + pathPattern.map {
            val key = if(it is Keyed) it.key else null
            if(key != null) resolver.generate(key, it) else it.generate(resolver)
        }.joinToString("/")
    }

    fun generateQuery(resolver: Resolver): Map<String, String> {
        return queryPattern.map { (parameterName, parameterPattern) ->
            parameterName to resolver.generate(parameterName, parameterPattern).toString()
        }.toMap()
    }

    fun newBasedOn(row: Row, resolver: Resolver): List<URLPattern> {
        val newResolver = withNumericStringPattern(resolver)

        val newPathPartsList = newBasedOn(pathPattern.map {
            val key = if(it is Keyed) it.key else null

            if(key !== null && row.containsField(key)) {
                val rowValue = row.getField(key)
                ExactMatchPattern(it.parse(rowValue, newResolver))
            } else it
        }, row, newResolver)
        val newQueryParamsList = newBasedOn(queryPattern, row, newResolver)

        return newPathPartsList.flatMap { newPathParts ->
            newQueryParamsList.map { newQueryParams ->
                URLPattern(newQueryParams, newPathParts, path)
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

internal fun toURLPattern(urlPattern: URI): URLPattern {
    val path = urlPattern.path

    val pathPattern = urlPattern.rawPath.trim('/').split("/").map { part ->
        when {
            isPatternToken(part) -> {
                val pieces = withoutPatternDelimiters(part).split(":").map { it.trim() }
                if(pieces.size != 2) {
                    throw ContractParseException("In path ${urlPattern.rawPath}, $part must be of the format (param_name:type), e.g. (id:number)")
                }

                val (name, type) = pieces

                LookupPattern(withPatternDelimiters(type), name)
            }
            else -> ExactMatchPattern(StringValue(part))
        }
    }

    val queryPattern = URIUtils.parseQuery(urlPattern.query).mapValues {
        if(isPatternToken(it.value))
            LookupPattern(it.value, it.key)
        else
            ExactMatchPattern(StringValue(it.value))
    }

    return URLPattern(queryPattern = queryPattern, path = path, pathPattern = pathPattern)
}
