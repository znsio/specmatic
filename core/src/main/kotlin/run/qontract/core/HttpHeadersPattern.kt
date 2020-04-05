package run.qontract.core

import run.qontract.core.pattern.*
import run.qontract.core.value.NullValue
import java.util.*

data class HttpHeadersPattern(val headers: Map<String, String> = emptyMap()) {
    fun matches(headers: HashMap<String, String>, resolver: Resolver) =
            headers to resolver.copy(patterns = resolver.patterns.plus("(number)" to NumericStringPattern())) to
                    ::matchEach otherwise
                    ::handleError toResult
                    ::returnResult

    fun matches(headers: Map<String, String>, resolver: Resolver): Result {
        val hashMap: HashMap<String, String> = HashMap()
        headers.forEach { (key, value) ->
            hashMap[key] = value
        }
        return matches(hashMap, resolver)
    }

    private fun matchEach(parameters: Pair<Map<String, String>, Resolver>): MatchingResult<Pair<Map<String, String>, Resolver>> {
        val (headers, resolver) = parameters
        this.headers.forEach { (key, value) ->
            val sampleValue = asValue(headers[key])
            if (sampleValue is NullValue)
                return MatchFailure(Result.Failure("""Header "$key" was not available"""))
            when (val result = asPattern(value, key).matches(asValue(headers[key]), resolver)) {
                is Result.Failure -> {
                    return MatchFailure(result.add("""Header "$key" did not match"""))
                }
            }
        }
        return MatchSuccess(parameters)
    }

    fun generate(resolver: Resolver): HashMap<String, String> {
        return HashMap(headers.mapValues { (key, value) ->
            asPattern(value, key).generate(resolver).toStringValue()
        }.toMutableMap())
    }

    private fun newBasedOn(row: Row, headers: Map<String, String>): Map<String, String> =
            HashMap(headers.mapValues {
                when {
                    row.containsField(it.key) -> row.getField(it.key)
                    else -> it.value
                }
            })

    fun newBasedOn(row: Row): List<HttpHeadersPattern> =
        multipleValidKeys(headers, row) { pattern ->
            listOf(newBasedOn(row, pattern))
        }.map { HttpHeadersPattern(it) }
}