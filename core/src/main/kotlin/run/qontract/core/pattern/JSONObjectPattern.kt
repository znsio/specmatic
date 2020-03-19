package run.qontract.core.pattern

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

        val resolverWithNumberType = resolver.copy().also {
            it.addCustomPattern("(number)", NumberTypePattern())
        }

        flatZip(pattern, sampleData.jsonObject).forEach { (key, patternValue, sampleValue) ->
            when (val result = asPattern(patternValue, key).matches(asValue(sampleValue), resolverWithNumberType)) {
                is Result.Failure -> return result.add("Expected: object[$key] to match $patternValue. Actual value: $sampleValue, in JSONObject ${sampleData.jsonObject}")
            }
        }

        return Result.Success()
    }

    override fun generate(resolver: Resolver) = JSONObjectValue(generate(pattern, resolver))

    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> = newBasedOn(pattern, row, resolver)
}

fun newBasedOn(jsonPattern: Map<String, Any?>, row: Row, resolver: Resolver): List<JSONObjectPattern> {
    return multipleValidKeys(jsonPattern, row) { pattern ->
        multipleValidValues(pattern, row, resolver)
    }.map { JSONObjectPattern(it) }
}

fun multipleValidValues(jsonPattern: Map<String, Any?>, row: Row, resolver: Resolver): List<Map<String, Any?>> {
    val patternCollection = jsonPattern.mapValues { asValue(it.value) }.mapValues { (patternKey, patternValue) ->
        when (patternValue) {
            is StringValue ->
                when {
                    isLazyPattern(patternValue.string) -> LazyPattern(patternValue.string, patternKey).newBasedOn(row, resolver).map { it.pattern }
                    row.containsField(withoutOptionality(patternKey)) -> {
                        val cleanedUpPatternKey = withoutOptionality(patternKey)
                        when {
                            isPatternToken(patternValue.string) -> {
                                val rowField = row.getField(cleanedUpPatternKey)?.toString() ?: ""
                                listOf(parsePrimitive(patternValue.string, rowField))
                            }
                            else -> listOf(row.getField(cleanedUpPatternKey))
                        }
                    }
                    else -> listOf(patternValue.string)
                }
            is JSONObjectValue -> multipleValidValues(patternValue.jsonObject, row, resolver)
            is JSONArrayValue -> newBasedOn(patternValue.list, row, resolver)
            else -> listOf(patternValue.value)
        }
    }

    return patternList(patternCollection)
}

fun generate(jsonPattern: MutableMap<String, Any?>, resolver: Resolver): MutableMap<String, Any?> =
    jsonPattern.mapKeys { entry -> withoutOptionality(entry.key) }.mapValues { (key, value) ->
        asPattern(asValue(value).value, key).generate(resolver).value
    }.toMutableMap()
