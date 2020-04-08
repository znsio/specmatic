package run.qontract.core

import run.qontract.core.pattern.*
import run.qontract.core.value.NullValue
import java.util.*

data class HttpHeadersPattern(val headers: Map<String, Pattern> = emptyMap()) {
    fun matches(headers: HashMap<String, String>, resolver: Resolver): Result {
        val result = headers to resolver.copy(patterns = resolver.patterns.plus("(number)" to NumericStringPattern())) to
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
        this.headers.forEach { (key, pattern) ->
            val sampleValue = headers[key] ?: return MatchFailure(Result.Failure(message = """Header was missing""", breadCrumb = key))

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

    fun generate(resolver: Resolver): HashMap<String, String> {
        return attempt(breadCrumb = "HEADERS") {
            HashMap(headers.mapValues { (key, pattern) ->
                attempt(breadCrumb = key) {
                    asPattern(pattern, key).generate(resolver).toStringValue()
                }
            }.toMutableMap())
        }
    }

    fun newBasedOn(row: Row, resolver: Resolver): List<HttpHeadersPattern> =
        multipleValidKeys(headers, row) { pattern ->
            newBasedOn(pattern, row, resolver)
        }.map { HttpHeadersPattern(it) }
}