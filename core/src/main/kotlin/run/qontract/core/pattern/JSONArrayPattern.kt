package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.utilities.jsonStringToArray
import run.qontract.core.Result
import run.qontract.core.value.*
import java.util.*
import javax.xml.parsers.ParserConfigurationException

@Throws(Exception::class)
private fun matchesRepeating(pattern: String, arraySample: List<Any?>, startingIndex: Int, resolver: Resolver): Result {
    for (index in startingIndex until arraySample.size) {
        when (val result = asPattern(pattern, null).matches(asValue(arraySample[index] ?: NoValue()), resolver)) {
            is Result.Failure -> return result.add("Expected array[$index] to match repeating pattern $pattern. Actual value: ${arraySample[index]} in array $arraySample")
        }
    }

    return Result.Success()
}

class JSONArrayPattern : Pattern {
    override val pattern = mutableListOf<Any?>()

    constructor(jsonContent: String) {
        pattern.addAll(jsonStringToArray(jsonContent))
    }

    constructor(jsonObject: List<Any?>) {
        pattern.addAll(jsonObject)
    }

    private fun failedMessage(value: JSONArrayValue) = "JSON Array did not match Expected: $pattern Actual: ${value.list}"

    private fun failed(value: JSONArrayValue) = Result.Failure(failedMessage(value))

    @Throws(Exception::class)
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if(sampleData !is JSONArrayValue)
            return Result.Failure("$sampleData is not JSON")

        if(sampleData.list.isEmpty())
            return Result.Success()

        for (index in 0 until pattern.size) {
            if(index == sampleData.list.size) return failed(sampleData)
            val sampleValue = sampleData.list[index]
            val patternValue = pattern[index]
            if (isRepeatingPattern(patternValue)) {
                when (val result = matchesRepeating(extractPatternFromRepeatingToken(patternValue as Any), sampleData.list, index, resolver)) {
                    is Result.Failure -> return result.add(failedMessage(sampleData))
                }
            } else {
                when (val result = asPattern(patternValue, null).matches(asValue(sampleValue?: NoValue()), resolver)) {
                    is Result.Failure -> return result.add("Expected array[$index] to match $patternValue. Actual value: $sampleValue in ${sampleData.list}")
                }
            }
        }

        return Result.Success()
    }

    override fun generate(resolver: Resolver): Value {
        return JSONArrayValue(generate(pattern, resolver))
    }

    override fun newBasedOn(row: Row, resolver: Resolver) = JSONArrayPattern(newBasedOn(pattern, row, resolver))
}

fun newBasedOn(jsonPattern: List<Any?>, row: Row, resolver: Resolver): List<Any?> =
    jsonPattern.map(::asValue).map {
        when(it) {
            is JSONObjectValue -> newBasedOn(it.jsonObject, row, resolver)
            is JSONArrayValue -> newBasedOn(it.list, row, resolver)
            else -> it.value
        }
    }

fun generate(jsonPattern: List<Any?>, resolver: Resolver): MutableList<Any?> =
    jsonPattern.flatMap {
        if (isRepeatingPattern(it)) {
            generateMultipleValues(extractPatternFromRepeatingToken(it as Any), resolver).toList()
        } else {
            listOf(asPattern(asValue(it).value, null).generate(resolver).value)
        }
    }.toMutableList()

internal fun generateMultipleValues(pattern: String, resolver: Resolver): List<Any> =
    0.until(randomNumber(10)).map {
        when(val result = asPattern(asValue(pattern).value, null).generate(resolver).value) {
            is Value -> result.value
            else -> result
        }
    }

internal fun randomNumber(max: Int) = Random().nextInt(max - 1) + 1
