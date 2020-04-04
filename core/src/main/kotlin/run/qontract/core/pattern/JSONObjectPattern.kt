package run.qontract.core.pattern

import run.qontract.core.*
import run.qontract.core.utilities.mapZip
import run.qontract.core.utilities.stringToPatternMap
import run.qontract.core.value.*

data class JSONObjectPattern(override val pattern: Map<String, Pattern> = emptyMap()) : Pattern {
    constructor(jsonContent: String) : this(stringToPatternMap(jsonContent))

    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if(sampleData is NullValue)
            return Result.Failure("Expected $pattern but got null")
        if(sampleData !is JSONObjectValue)
            return Result.Failure("Unable to interpret ${sampleData?.value} as JSON")

        val missingKey = pattern.keys.find { key -> isMissingKey(sampleData.jsonObject, key) }
        if(missingKey != null)
            return Result.Failure("Missing key $missingKey in ${sampleData.jsonObject}")

        mapZip(pattern, sampleData.jsonObject).forEach { (key, patternValue, sampleValue) ->
            when (val result = asPattern(patternValue, key).matches(asValue(sampleValue), withNumberTypePattern(resolver))) {
                is Result.Failure -> return result.add("Expected value at $key to match $patternValue, actual value $sampleValue in JSONObject ${sampleData.jsonObject}")
            }
        }

        return Result.Success()
    }

    override fun generate(resolver: Resolver) = JSONObjectValue(generate(pattern, resolver))

    override fun newBasedOn(row: Row, resolver: Resolver): List<JSONObjectPattern> =
            newBasedOn(pattern, row, resolver).map { JSONObjectPattern(it) }
    override fun parse(value: String, resolver: Resolver): Value = parsedJSON(value)
}

fun generate(jsonPattern: Map<String, Pattern>, resolver: Resolver): Map<String, Value> =
    jsonPattern.mapKeys { entry -> withoutOptionality(entry.key) }.mapValues { (key, value) ->
        asPattern(value, key).generate(resolver)
    }
