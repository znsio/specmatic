package run.qontract.core

import run.qontract.core.pattern.*
import run.qontract.core.utilities.URIUtils
import run.qontract.core.value.NullValue
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value
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
            return Result.Failure("Expected $uri to have ${pathPattern.size} parts, but it has ${pathParts.size} parts.", breadCrumb = "PATH")

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

    val pathPattern = urlPattern.rawPath.trim('/').split("/").filter { it.isNotEmpty() }.map { part ->
        when {
            isPatternToken(part) -> {
                val pieces = withoutPatternDelimiters(part).split(":").map { it.trim() }
                if(pieces.size != 2) {
                    throw ContractException("In path ${urlPattern.rawPath}, $part must be of the format (param_name:type), e.g. (id:number)")
                }

                val (name, type) = pieces

                URLPathPattern(LookupPattern(withPatternDelimiters(type)), name)
            }
            else -> URLPathPattern(ExactMatchPattern(StringValue(part)))
        }
    }

    val queryPattern = URIUtils.parseQuery(urlPattern.query).mapValues {
        if(isPatternToken(it.value))
            LookupPattern(it.value, it.key)
        else
            ExactMatchPattern(StringValue(it.value))
    }

    return URLMatcher(queryPattern = queryPattern, path = path, pathPattern = pathPattern)
}

data class URLPathPattern (override val pattern: Pattern, val key: String? = null) : Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result =
            resolver.matchesPattern(key, pattern, sampleData ?: NullValue)

    override fun generate(resolver: Resolver): Value =
            if(key != null) resolver.generate(key, pattern) else pattern.generate(resolver)

    override fun newBasedOn(row: Row, resolver: Resolver): List<URLPathPattern> =
            pattern.newBasedOn(row, resolver).map { URLPathPattern(it, key) }

    override fun parse(value: String, resolver: Resolver): Value = pattern.parse(value, resolver)

}
