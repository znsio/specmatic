package run.qontract.core

import run.qontract.core.pattern.*
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value

data class HttpHeadersPattern(val pattern: Map<String, Pattern> = emptyMap()) {
    fun matches(headers: Map<String, String>, resolver: Resolver): Result {
        val result = headers to resolver.copy(newPatterns = resolver.newPatterns.plus("(number)" to NumericStringPattern)) to
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

        this.pattern.forEach { (key, pattern) ->
            val sampleValue = headers[key] ?: return MatchFailure(Result.Failure(message = """Header $key was missing""", breadCrumb = key))

            try {
                when (val result = resolver.matchesPattern(key, pattern, attempt(breadCrumb = key) { pattern.parse(sampleValue, resolver) })) {
                    is Result.Failure -> {
                        return MatchFailure(result.breadCrumb(key))
                    }
                }
            } catch(e: ContractException) {
                return MatchFailure(e.result())
            }
        }

        return MatchSuccess(parameters)
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
        multipleValidKeys(pattern, row) { pattern ->
            newBasedOn(pattern, row, resolver)
        }.map { HttpHeadersPattern(it) }
}