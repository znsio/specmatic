package run.qontract.core.pattern

import com.ctc.wstx.shaded.msv_core.grammar.Grammar
import run.qontract.core.Resolver
import run.qontract.core.utilities.jsonStringToMap
import run.qontract.core.Result
import run.qontract.core.utilities.flatZip
import run.qontract.core.value.*

class JSONObjectPattern : Pattern {
    override val pattern = mutableMapOf<String, Any?>()

    constructor(data: Map<String, Any?>) {
        pattern.putAll(data)
    }

    constructor(jsonContent: String) {
        pattern.putAll(jsonStringToMap(jsonContent))
    }

    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if(sampleData !is JSONObjectValue)
            return Result.Failure("Unable to interpret ${sampleData?.value} as JSON")

        val missingKey = pattern.keys.find { key -> isMissingKey(sampleData.jsonObject, key) }
        if(missingKey != null)
            return Result.Failure("Missing key $missingKey in ${sampleData.jsonObject}")

        flatZip(pattern, sampleData.jsonObject).forEach { (key, patternValue, sampleValue) ->
            when (val result = asPattern(patternValue, key).matches(asValue(sampleValue), resolver)) {
                is Result.Failure -> return result.add("Expected: object[$key] to match $patternValue. Actual value: $sampleValue, in JSONObject ${sampleData.jsonObject}")
            }
        }

        return Result.Success()
    }

    override fun generate(resolver: Resolver) = JSONObjectValue(generate(pattern, resolver))
    override fun newBasedOn(row: Row, resolver: Resolver) = JSONObjectPattern(newBasedOn(pattern, row, resolver))
}

fun newBasedOn(jsonPattern: Map<String, Any?>, row: Row, resolver: Resolver): Map<String, Any?> {
    return jsonPattern.mapValues { asValue(it.value) }.mapValues { (patternKey, patternValue) ->
        when (patternValue) {
            is StringValue ->
                when {
                    isLazyPattern(patternValue.string) -> LazyPattern(patternValue.string, patternKey).newBasedOn(row, resolver).pattern
                    row.containsField(cleanupKey(patternKey)) -> {
                        val cleanedUpPatternKey = cleanupKey(patternKey)
                        when {
                            isPatternToken(patternValue.string) -> {
                                val rowField = row.getField(cleanedUpPatternKey)?.toString() ?: ""
                                parsePrimitive(patternValue.string, rowField)
                            }
                            else -> row.getField(cleanedUpPatternKey)
                        }
                    }
                    else -> patternValue.string
                }
            is JSONObjectValue -> newBasedOn(patternValue.jsonObject, row, resolver)
            is JSONArrayValue -> newBasedOn(patternValue.list, row, resolver)
            else -> patternValue.value
        }
    }
}

fun generate(jsonPattern: MutableMap<String, Any?>, resolver: Resolver): MutableMap<String, Any?> =
    jsonPattern.mapKeys { entry -> cleanupKey(entry.key) }.mapValues { (key, value) ->
        asPattern(asValue(value).value, key).generate(resolver).value
    }.toMutableMap()

