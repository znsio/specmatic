package run.qontract.core

import run.qontract.core.pattern.*
import run.qontract.core.utilities.URIUtils
import java.net.URI
import javax.xml.parsers.ParserConfigurationException

class URLMatcher : Cloneable {
    private val queryPattern: HashMap<String, String>
    private val path: String
    private val pathParameters: HashMap<String, String>

    constructor(urlPattern: URI) {
        path = urlPattern.path
        pathParameters = URIUtils.parsePathParams(urlPattern.rawPath)
        queryPattern = URIUtils.parseQuery(urlPattern.query)
    }

    constructor(other: URLMatcher, row: Row, resolver: Resolver) {
        this.path = other.path

        this.pathParameters = HashMap()
        this.pathParameters.putAll(other.pathParameters)
        populate(this.pathParameters, row, resolver)

        this.queryPattern = HashMap()
        this.queryPattern.putAll(other.queryPattern)
        populate(this.queryPattern, row, resolver)
    }

    fun matches(uri: URI, sampleQuery: HashMap<String, String>, resolver: Resolver): Result {
        resolver.addCustomPattern("(number)", NumericStringPattern())

        matchesPath(uri, resolver).let {
            return when (it) {
                is Result.Success -> {
                    matchesQuery(sampleQuery, resolver)
                }
                else -> it
            }
        }
    }

    private fun matchesPath(uri: URI, resolver: Resolver): Result {
        val pathParts = uri.path.split("/".toRegex()).filter { it.isNotEmpty() }.toTypedArray()
        val pathPatternParts = path.split("/".toRegex()).filter { it.isNotEmpty() }.toTypedArray()

        if (pathPatternParts.size != pathParts.size)
            return Result.Failure("Number path parts do not match. Expected: ${pathPatternParts.size} Actual: ${pathParts.size}")

        for (position in pathPatternParts.indices) {
            if (pathPatternParts[position].contains("(")) {
                val partPattern = pathPatternParts[position].removeSurrounding("(", ")").split(":".toRegex()).toTypedArray()[1]
                when (val result = resolver.matchesPattern(null, "($partPattern)", pathParts[position])) {
                    is Result.Failure -> return result.add("Path part did not match. Expected: $partPattern Actual: ${pathParts[position]}")
                }
            } else {
                if (pathPatternParts[position] != pathParts[position]) {
                    return Result.Failure("Path part did not match. Expected: ${pathPatternParts[position]} Actual: ${pathParts[position]}")
                }
            }
        }

        return Result.Success()
    }

    private fun matchesQuery(sampleQuery: HashMap<String, String>, resolver: Resolver): Result {
        for (key in queryPattern.keys) {
            if (!sampleQuery.containsKey(key)) {
                return Result.Failure("Query parameter not present. Expected: $key Actual: ")
            }
            val patternValue = queryPattern[key]
            val sampleValue = sampleQuery[key]
            when (val result = resolver.matchesPattern(key, patternValue!!, sampleValue!!)) {
                is Result.Failure -> return result.add("Query parameter did not match")
            }
        }
        return Result.Success()
    }

    fun generatePath(resolver: Resolver?): String {
        if (!path.contains("(")) return path

        var pathToBeSubstituted = path

        for ((parameterName, parameterPattern) in pathParameters) {
            try {
                var parameterValue = parameterPattern
                if (parameterValue.contains("(")) {
                    parameterValue = resolver?.generate(parameterName, parameterValue).toString()
                }
                pathToBeSubstituted = pathToBeSubstituted.replaceFirst(Regex("\\($parameterName:.*\\)"), parameterValue)
            } catch (e: Exception) {
                throw RuntimeException("Error substituting pattern with generated value", e)
            }
        }

        return pathToBeSubstituted
    }

    fun generateQuery(resolver: Resolver?): HashMap<String, String> {
        return queryPattern.map { (parameterName, parameterPattern) ->
            parameterName to resolver?.generateValue(parameterName, parameterPattern).toString()
        }.toMap(HashMap())
    }

    fun newBasedOn(row: Row, resolver: Resolver): URLMatcher {
        resolver.addCustomPattern("(number)", NumericStringPattern())

        return URLMatcher(this, row, resolver)
    }

    fun newPatternsBasedOn(row: Row, resolver: Resolver): List<URLMatcher> {
        resolver.addCustomPattern("(number)", NumericStringPattern())
        return listOf(URLMatcher(this, row, resolver))
    }

    private fun populate(parameters: HashMap<String, String>, row: Row, resolver: Resolver) {
        parameters
                .filterKeys { key -> row.containsField(key) }
                .forEach { (key, mapValue) ->
                    val rowValue = row.getField(key).toString()
                    if(isPatternToken(mapValue)) {
                        if (!resolver.matchesPattern(key, mapValue, rowValue).toBoolean()) {
                            throw ContractParseException("Example has $rowValue. But expected pattern $mapValue")
                        }
                    }

                    parameters[key] = row.getField(key).toString()
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