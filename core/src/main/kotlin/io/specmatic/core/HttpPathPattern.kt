package io.specmatic.core

import io.specmatic.core.Result.Failure
import io.specmatic.core.Result.Success
import io.specmatic.core.pattern.*
import io.specmatic.core.value.StringValue
import io.ktor.util.reflect.*
import io.specmatic.conversions.convertPathParameterStyle
import java.net.URI
import kotlin.collections.joinToString

val OMIT = listOf("(OMIT)", "(omit)")

const val PATH_BREAD_CRUMB = "PATH"

data class HttpPathPattern(
    val pathSegmentPatterns: List<URLPathSegmentPattern>,
    val path: String
) {
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
        return matches(httpRequest, resolver)
    }

    fun matches(httpRequest: HttpRequest, resolver: Resolver): Result {
        val path = httpRequest.path!!
        val pathSegments = path.split("/".toRegex()).filter { it.isNotEmpty() }.toTypedArray()

        if (pathSegmentPatterns.size != pathSegments.size)
            return Failure(
                "Expected $path (having ${pathSegments.size} path segments) to match ${this.path} (which has ${pathSegmentPatterns.size} path segments).",
                breadCrumb = PATH_BREAD_CRUMB,
                failureReason = FailureReason.URLPathMisMatch
            )

        val results = pathSegmentPatterns.zip(pathSegments).map { (urlPathPattern, token) ->
            try {

                val parsedValue = urlPathPattern.tryParse(token, resolver)
                val result = resolver.matchesPattern(urlPathPattern.key, urlPathPattern.pattern, parsedValue)
                if (result is Failure) {
                    when (urlPathPattern.key) {
                        null -> result.breadCrumb("$PATH_BREAD_CRUMB ($path)").withFailureReason(FailureReason.URLPathMisMatch)
                        else -> result.breadCrumb(urlPathPattern.key).breadCrumb(PATH_BREAD_CRUMB)
                    }
                } else {
                    Success()
                }
            } catch (e: ContractException) {
                e.failure().breadCrumb("$PATH_BREAD_CRUMB ($path)").let { failure ->
                    urlPathPattern.key?.let { failure.breadCrumb(urlPathPattern.key) } ?: failure
                }.withFailureReason(FailureReason.URLPathMisMatch)
            } catch (e: Throwable) {
                Failure(e.localizedMessage).breadCrumb("$PATH_BREAD_CRUMB ($path)").let { failure ->
                    urlPathPattern.key?.let { failure.breadCrumb(urlPathPattern.key) } ?: failure
                }.withFailureReason(FailureReason.URLPathMisMatch)
            }
        }

        val failures = results.filterIsInstance<Failure>()

        val finalMatchResult = Result.fromResults(failures)

        val failureReason = if (structureMatches(path, resolver)) {
            FailureReason.URLPathParamMismatchButSameStructure
        } else FailureReason.URLPathMisMatch

        return finalMatchResult.withFailureReason(failureReason)
    }

    fun generate(resolver: Resolver): String {
        return attempt(breadCrumb = PATH_BREAD_CRUMB) {
            ("/" + pathSegmentPatterns.mapIndexed { index, urlPathPattern ->
                attempt(breadCrumb = "[$index]") {
                    val key = urlPathPattern.key
                    resolver.withCyclePrevention(urlPathPattern.pattern) { cyclePreventedResolver ->
                        if (key != null)
                            cyclePreventedResolver.generate("PATH-PARAMS", key, urlPathPattern.pattern)
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
    ): Sequence<List<URLPathSegmentPattern>> {
        val generatedPatterns = newListBasedOn(pathSegmentPatterns.mapIndexed { index, urlPathParamPattern ->
                val key = urlPathParamPattern.key
                if (key === null || !row.containsField(key)) return@mapIndexed urlPathParamPattern
                attempt(breadCrumb = "$PATH_BREAD_CRUMB.${withoutOptionality(key)}") {
                    val rowValue = row.getField(key)
                    when {
                        isPatternToken(rowValue) -> attempt("Pattern mismatch in example of path param \"${urlPathParamPattern.key}\"") {
                            val rowValueWithoutWithoutIdentifier = withoutPatternDelimiters(rowValue).split(':').let {
                                it.getOrNull(1) ?: it.getOrNull(0) ?: throw ContractException("Invalid pattern token $rowValue in example")
                            }.let {
                                withPatternDelimiters(it)
                            }
                            val rowPattern = resolvedHop(resolver.getPattern(rowValueWithoutWithoutIdentifier), resolver)
                            val pathSegmentPattern = resolvedHop(urlPathParamPattern.pattern, resolver)

                            if(pathSegmentPattern.javaClass == rowPattern.javaClass) {
                                urlPathParamPattern
                            } else {
                                when (val result = urlPathParamPattern.encompasses(rowPattern, resolver, resolver)) {
                                    is Success -> urlPathParamPattern.copy(pattern = rowPattern)
                                    is Failure -> throw ContractException(result.toFailureReport())
                                }
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
            }, row, resolver).map { it.value }

        //TODO: replace this with Generics
        return generatedPatterns.map { list -> list.map { it as URLPathSegmentPattern } }
    }

    fun readFrom(row: Row, resolver: Resolver): Sequence<List<URLPathSegmentPattern>> {
        val generatedPatterns = newListBasedOn(pathSegmentPatterns.map { urlPathParamPattern ->
            val key = urlPathParamPattern.key
            if (key === null || !row.containsField(key)) return@map urlPathParamPattern

            attempt(breadCrumb = "$PATH_BREAD_CRUMB.${withoutOptionality(key)}") {
                val rowValue = row.getField(key)
                when {
                    isPatternToken(rowValue) ->  {
                        val parts = withoutPatternDelimiters(rowValue).split(':')
                        val tokenBody = parts.getOrNull(1) ?: parts.getOrNull(0) ?: throw ContractException("Invalid pattern token $rowValue in example")
                        val pattern = resolver.getPattern(withPatternDelimiters(tokenBody))
                        resolvedHop(pattern, resolver)
                    }
                    else ->  {
                        val exactValue = parsedScalarValue(rowValue)
                        URLPathSegmentPattern(ExactValuePattern(exactValue))
                    }
                }
            }
        }, row, resolver).map { it.value }

        //TODO: replace this with Generics
        return generatedPatterns.map { list -> list.map { it as URLPathSegmentPattern } }
    }

    fun newBasedOn(resolver: Resolver): Sequence<List<URLPathSegmentPattern>> {
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
        return convertPathParameterStyle(this.path)
    }

    fun pathParameters(): List<URLPathSegmentPattern> {
        return pathSegmentPatterns.filter { !it.pattern.instanceOf(ExactValuePattern::class) }
    }

    fun withWildcardPathSegments(): HttpPathPattern {
        return this.copy(
            pathSegmentPatterns = this.pathSegmentPatterns.map {
                if (it.pattern is ExactValuePattern) it
                else it.copy(pattern = AnythingPattern)
            }
        )
    }

    private fun negatively(
        patterns: List<URLPathSegmentPattern>,
        row: Row,
        resolver: Resolver
    ): Sequence<ReturnValue<List<URLPathSegmentPattern>>> {
        return Sequence {
            patterns.associateWith { it.negativeBasedOn(row, resolver) }
                .flatMap { (pathSegmentPattern, negativePatterns) ->
                    negativePatterns.map { negativePatternR ->
                        negativePatternR.ifValue { negativePattern ->
                            patterns.map {
                                if (it == pathSegmentPattern)
                                    negativePattern
                                else
                                    it
                            }.filterIsInstance<URLPathSegmentPattern>()
                        }
                    }
                }.iterator()
        }
    }

    fun negativeBasedOn(
        row: Row,
        resolver: Resolver
    ): Sequence<ReturnValue<List<URLPathSegmentPattern>>> {
        return negatively(pathSegmentPatterns, row, resolver)
    }

    private fun patternFromExample(
        key: String?,
        row: Row,
        urlPathPattern: URLPathSegmentPattern,
        resolver: Resolver
    ): Sequence<ReturnValue<Pattern>> = when {
        key !== null && row.containsField(key) -> {
            val rowValue = row.getField(key)
            when {
                isPatternToken(rowValue) -> attempt("Pattern mismatch in example of path param \"${urlPathPattern.key}\"") {
                    val rowPattern = resolver.getPattern(rowValue)
                    when (val result = urlPathPattern.encompasses(rowPattern, resolver, resolver)) {
                        is Success -> sequenceOf(urlPathPattern.copy(pattern = rowPattern))
                        is Failure -> throw ContractException(result.toFailureReport())
                    }
                }

                else -> attempt("Format error in example of path parameter \"$key\"") {
                    val value = urlPathPattern.parse(rowValue, resolver)

                    val matchResult = urlPathPattern.matches(value, resolver)
                    if (matchResult is Failure)
                        throw ContractException("""Could not run contract test, the example value ${value.toStringLiteral()} provided "id" does not match the contract.""")

                    sequenceOf(URLPathSegmentPattern(ExactValuePattern(value)))
                }
            }.map { HasValue(it) }
        }

        else -> returnValueSequence {
            val positives: Sequence<Pattern> = urlPathPattern.newBasedOnWrapper(row, resolver)
            val negatives: Sequence<ReturnValue<Pattern>> = urlPathPattern.negativeBasedOn(row, resolver)

            positives.map { HasValue(it) } + negatives
        }
    }

    fun extractPathParams(requestPath: String, resolver: Resolver): Map<String, String> {
        val pathSegments = requestPath.split("/").filter { it.isNotEmpty() }

        return pathSegmentPatterns.zip(pathSegments).mapNotNull { (pattern, value) ->
            when {
                pattern.pattern is ExactValuePattern -> null
                else -> pattern.key!! to value
            }
        }.toMap()
    }

    fun fixValue(path: String?, resolver: Resolver): String {
        if (path == null) return this.generate(resolver)

        val pathSegments = path.split("/".toRegex()).filter { it.isNotEmpty() }.map(::removeKeyFromParameterToken)
        if (pathSegmentPatterns.size != pathSegments.size) return this.generate(resolver)

        val pathHadPrefix = path.startsWith("/")
        return pathSegmentPatterns.zip(pathSegments).map { (urlPathPattern, token) ->
            val updatedResolver = resolver.updateLookupPath("PATH-PARAMS", urlPathPattern.key.orEmpty())
            urlPathPattern.fixValue(urlPathPattern.tryParse(token, updatedResolver), updatedResolver)
        }.joinToString("/", prefix = "/".takeIf { pathHadPrefix }.orEmpty() )
    }

    fun fillInTheBlanks(path: String?, resolver: Resolver): ReturnValue<String> {
        if (path == null) return HasFailure("Path cannot be null")

        val pathSegments = path.split("/").filter { it.isNotEmpty() }.map(::removeKeyFromParameterToken)
        if (pathSegmentPatterns.size != pathSegments.size) {
            return HasFailure("Expected ${pathSegmentPatterns.size} path segments but got ${pathSegments.size}")
        }

        val pathHadPrefix = path.startsWith("/")
        val generatedSegments = pathSegmentPatterns.zip(pathSegments).map { (urlPathPattern, token) ->
            val updatedResolver = resolver.updateLookupPath("PATH-PARAMS", urlPathPattern.key.orEmpty())
            urlPathPattern.fillInTheBlanks(urlPathPattern.tryParse(token, updatedResolver), updatedResolver).breadCrumb(urlPathPattern.key)
        }.listFold()

        return generatedSegments.ifValue { value ->
            value.joinToString(separator = "/", prefix = "/".takeIf { pathHadPrefix }.orEmpty()) {
                it.toStringLiteral()
            }
        }
    }

    private fun removeKeyFromParameterToken(token: String): String {
        if (!isPatternToken(token) || !token.contains(":")) return token
        val patternType = withoutPatternDelimiters(token).split(":").last()
        return withPatternDelimiters(patternType)
    }

    private fun structureMatches(path: String, resolver: Resolver): Boolean {
        val pathSegments = path.split("/").filter(String::isNotEmpty)
        if (pathSegments.size != pathSegmentPatterns.size) return false

        pathSegmentPatterns.zip(pathSegments).forEach { (pattern, segment) ->
            if (pattern.pattern !is ExactValuePattern && pattern.key != null) return@forEach
            val parsedSegment = pattern.tryParse(segment, resolver)
            val result = pattern.matches(parsedSegment, resolver)
            if (result is Failure) return false
        }

        return true
    }

    companion object {
        fun from(path: String): HttpPathPattern {
            return buildHttpPathPattern(path)
        }
    }
}

fun buildHttpPathPattern(
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

