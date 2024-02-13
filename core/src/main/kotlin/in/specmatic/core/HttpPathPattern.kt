package `in`.specmatic.core

import `in`.specmatic.core.Result.Failure
import `in`.specmatic.core.Result.Success
import `in`.specmatic.core.pattern.*
import `in`.specmatic.core.value.StringValue
import io.ktor.util.reflect.*
import java.net.URI

val OMIT = listOf("(OMIT)", "(omit)")

data class HttpPathPattern(
    val pathSegmentPatterns: List<URLPathSegmentPattern>,
    val path: String
) {
    fun complexity(): ULong {
        return pathSegmentPatterns.fold(1.toULong()) { acc, pattern -> acc * pattern.complexity(Resolver()) }
    }

    fun encompasses(otherHttpPathPattern: HttpPathPattern, thisResolver: Resolver, otherResolver: Resolver): Result {
        if (this.matches(URI.create(otherHttpPathPattern.path), resolver=thisResolver) is Success)
            return Success()

        val mismatchedPartResults =
            this.pathSegmentPatterns.zip(otherHttpPathPattern.pathSegmentPatterns).map { (thisPathItem, otherPathItem) ->
                thisPathItem.pattern.encompasses(otherPathItem, thisResolver, otherResolver)
            }

        val failures = mismatchedPartResults.filterIsInstance<Failure>()

        if (failures.isEmpty())
            return Success()

        return Result.fromFailures(failures)
    }

    fun matches(uri: URI, resolver: Resolver = Resolver()): Result {
        return matches(uri.path, resolver)
    }

    fun matches(path: String, resolver: Resolver): Result {
        val httpRequest = HttpRequest(path = path)
        return matches(httpRequest, resolver).withFailureReason(FailureReason.URLPathMisMatch)
    }

    fun matches(httpRequest: HttpRequest, resolver: Resolver): Result {
        val path = httpRequest.path!!
        val pathSegments = path.split("/".toRegex()).filter { it.isNotEmpty() }.toTypedArray()

        if (pathSegmentPatterns.size != pathSegments.size)
            return Failure(
                "Expected $path (having ${pathSegments.size} path segments) to match ${this.path} (which has ${pathSegmentPatterns.size} path segments).",
                breadCrumb = "PATH"
            )

        pathSegmentPatterns.zip(pathSegments).forEach { (urlPathPattern, token) ->
            try {
                val parsedValue = urlPathPattern.tryParse(token, resolver)
                val result = resolver.matchesPattern(urlPathPattern.key, urlPathPattern.pattern, parsedValue)
                if (result is Failure) {
                    return when (urlPathPattern.key) {
                        null -> result.breadCrumb("PATH ($path)")
                        else -> result.breadCrumb("PATH ($path)").breadCrumb(urlPathPattern.key)
                    }
                }
            } catch (e: ContractException) {
                e.failure().breadCrumb("PATH ($path)").let { failure ->
                    urlPathPattern.key?.let { failure.breadCrumb(urlPathPattern.key) } ?: failure
                }
            } catch (e: Throwable) {
                Failure(e.localizedMessage).breadCrumb("PATH ($path)").let { failure ->
                    urlPathPattern.key?.let { failure.breadCrumb(urlPathPattern.key) } ?: failure
                }
            }
        }

        return Success()
    }

    fun generate(resolver: Resolver): String {
        return attempt(breadCrumb = "PATH") {
            ("/" + pathSegmentPatterns.mapIndexed { index, urlPathPattern ->
                attempt(breadCrumb = "[$index]") {
                    val key = urlPathPattern.key
                    resolver.withCyclePrevention(urlPathPattern.pattern) { cyclePreventedResolver ->
                        if (key != null)
                            cyclePreventedResolver.generate(key, urlPathPattern.pattern)
                        else urlPathPattern.pattern.generate(cyclePreventedResolver)
                    }
                }
            }.joinToString("/")).let {
                if (path.endsWith("/") && !it.endsWith("/")) "$it/" else it
            }.let {
                if (path.startsWith("/") && !it.startsWith("/")) "$/it" else it
            }
        }
    }

    fun newBasedOn(
        row: Row,
        resolver: Resolver
    ): List<List<URLPathSegmentPattern>> {
        val generatedPatterns = newBasedOn(pathSegmentPatterns.mapIndexed { index, urlPathParamPattern ->
            val key = urlPathParamPattern.key
            if (key === null || !row.containsField(key)) return@mapIndexed urlPathParamPattern
            attempt(breadCrumb = "[$index]") {
                val rowValue = row.getField(key)
                when {
                    isPatternToken(rowValue) -> attempt("Pattern mismatch in example of path param \"${urlPathParamPattern.key}\"") {
                        val rowPattern = resolver.getPattern(rowValue)
                        when (val result = urlPathParamPattern.encompasses(rowPattern, resolver, resolver)) {
                            is Success -> urlPathParamPattern.copy(pattern = rowPattern)
                            is Failure -> throw ContractException(result.toFailureReport())
                        }
                    }

                    else -> attempt("Format error in example of path parameter \"$key\"") {
                        val value = urlPathParamPattern.parse(rowValue, resolver)

                        val matchResult = urlPathParamPattern.matches(value, resolver)
                        if (matchResult is Failure)
                            throw ContractException("""Could not run contract test, the example value ${value.toStringLiteral()} provided "id" does not match the contract.""")

                        URLPathSegmentPattern(
                            ExactValuePattern(
                                value
                            )
                        )
                    }
                }
            }
        }, row, resolver)

        //TODO: replace this with Generics
        return generatedPatterns.map { list -> list.map { it as URLPathSegmentPattern } }
    }

    fun newBasedOn(resolver: Resolver): List<List<URLPathSegmentPattern>> {
        val generatedPatterns = newBasedOn(pathSegmentPatterns.mapIndexed { index, urlPathPattern ->
            attempt(breadCrumb = "[$index]") {
                urlPathPattern
            }
        }, resolver)

        //TODO: replace this with Generics
        return generatedPatterns.map { list -> list.map { it as URLPathSegmentPattern } }
    }

    override fun toString(): String {
        return path
    }

    fun toOpenApiPath(): String {
        val pathParamsWithPattern =
            this.path.split("/").filter { it.startsWith("(") }.map { it.replace("(", "").replace(")", "").split(":") }
        return this.path.replace("(", "{").replace(""":[a-z,A-Z]*?\)""".toRegex(), "}")
    }

    fun pathParameters(): List<URLPathSegmentPattern> {
        return pathSegmentPatterns.filter { !it.pattern.instanceOf(ExactValuePattern::class) }
    }

    private fun negatively(patterns: List<URLPathSegmentPattern>, row: Row, resolver: Resolver): List<List<URLPathSegmentPattern>> {
        if(patterns.isEmpty())
            return emptyList()

        val current = patterns.first()

        val negativesOfCurrent = current.negativeBasedOn(row, resolver).map { negative ->
            listOf(negative) + positively(patterns.drop(1), row, resolver)
        }.map { it.filterIsInstance<URLPathSegmentPattern>() }

        if(patterns.size == 1)
            return negativesOfCurrent



        val negativesFromSubsequent =
            negatively(patterns.drop(1), row, resolver)

        val negativesFromSubsequent1: List<List<URLPathSegmentPattern>> = negativesFromSubsequent
            .filterNot { it.isEmpty() }
            .flatMap { subsequentNegatives: List<URLPathSegmentPattern> ->
                val subsequents = current.newBasedOn(row, resolver).map { positive ->
                    listOf(positive) + subsequentNegatives
                }
                subsequents
            }

        return (negativesOfCurrent + negativesFromSubsequent1)
    }

    private fun positively(
        patterns: List<URLPathSegmentPattern>,
        row: Row,
        resolver: Resolver
    ): List<URLPathSegmentPattern> {
        if(patterns.isEmpty())
            return emptyList()

        val patternToPositively = patterns.first()

        val positives = patternFromExample(null, row, patternToPositively, resolver)

        return positives.map {
            listOf(it) + positively(patterns.drop(1), row, resolver)
        }.filterIsInstance<URLPathSegmentPattern>()
    }

    fun negativeBasedOn(
        row: Row,
        resolver: Resolver
    ): List<List<URLPathSegmentPattern>> {
        return negatively(pathSegmentPatterns, row, resolver)
    }

    private fun patternFromExample(
        key: String?,
        row: Row,
        urlPathPattern: URLPathSegmentPattern,
        resolver: Resolver
    ) = when {
        key !== null && row.containsField(key) -> {
            val rowValue = row.getField(key)
            when {
                isPatternToken(rowValue) -> attempt("Pattern mismatch in example of path param \"${urlPathPattern.key}\"") {
                    val rowPattern = resolver.getPattern(rowValue)
                    when (val result = urlPathPattern.encompasses(rowPattern, resolver, resolver)) {
                        is Success -> listOf(urlPathPattern.copy(pattern = rowPattern))
                        is Failure -> throw ContractException(result.toFailureReport())
                    }
                }

                else -> attempt("Format error in example of path parameter \"$key\"") {
                    val value = urlPathPattern.parse(rowValue, resolver)

                    val matchResult = urlPathPattern.matches(value, resolver)
                    if (matchResult is Failure)
                        throw ContractException("""Could not run contract test, the example value ${value.toStringLiteral()} provided "id" does not match the contract.""")

                    listOf(
                        URLPathSegmentPattern(
                            ExactValuePattern(
                                value
                            )
                        )
                    )
                }
            }
        }

        else -> (urlPathPattern.newBasedOn(row, resolver) + urlPathPattern.negativeBasedOn(row, resolver)).distinct()
    }
}

internal fun buildHttpPathPattern(
    url: String
): HttpPathPattern =
    buildHttpPathPattern(URI.create(url))

internal fun buildHttpPathPattern(
    urlPattern: URI
): HttpPathPattern {
    val path = urlPattern.path
    val pathPattern = pathToPattern(urlPattern.rawPath)
    return HttpPathPattern(path = path, pathSegmentPatterns = pathPattern)
}

internal fun pathToPattern(rawPath: String): List<URLPathSegmentPattern> =
    rawPath.trim('/').split("/").filter { it.isNotEmpty() }.map { part ->
        when {
            isPatternToken(part) -> {
                val pieces = withoutPatternDelimiters(part).split(":").map { it.trim() }
                if (pieces.size != 2) {
                    throw ContractException("In path ${rawPath}, $part must be of the format (param_name:type), e.g. (id:number)")
                }

                val (name, type) = pieces

                URLPathSegmentPattern(DeferredPattern(withPatternDelimiters(type)), name)
            }

            else -> URLPathSegmentPattern(ExactValuePattern(StringValue(part)))
        }
    }

