package run.qontract.core.pattern

import run.qontract.core.ContractParseException
import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.utilities.stringTooPatternArray
import run.qontract.core.value.*
import java.util.*

data class JSONArrayPattern(override val pattern: List<Pattern> = emptyList()) : Pattern {
    constructor(jsonString: String) : this(stringTooPatternArray(jsonString))

    private fun failedMessage(value: JSONArrayValue) = "JSON Array did not match Expected: $pattern Actual: ${value.list}"

    private fun failed(value: JSONArrayValue) = Result.Failure(failedMessage(value))

    @Throws(Exception::class)
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if(sampleData !is JSONArrayValue)
            return Result.Failure("$sampleData is not JSON")

        if(sampleData.list.isEmpty())
            return Result.Success()

        val resolverWithNumberType = resolver.copy().apply {
            addCustomPattern("(number)", NumberTypePattern())
        }

        for (index in pattern.indices) {
            if(index == sampleData.list.size) return failed(sampleData)
            val sampleValue = sampleData.list[index]

            when(val patternValue = pattern[index]) {
                is RepeatingPattern -> {
                    val slice = sampleData.list.slice(index..sampleData.list.lastIndex)
                    return when (val result = patternValue.matches(JSONArrayValue(slice), resolver)) {
                        is Result.Failure -> result.add(failedMessage(sampleData))
                        else -> result
                    }
                }
                else -> {
                    when (val result = patternValue.matches(sampleValue, resolverWithNumberType)) {
                        is Result.Failure -> return result.add("Expected value at index $index to match $patternValue. Actual value: $sampleValue in ${sampleData.list}")
                    }
                }
            }
        }

        return Result.Success()
    }

    override fun generate(resolver: Resolver): Value {
        return JSONArrayValue(generate(pattern, resolver))
    }

    override fun newBasedOn(row: Row, resolver: Resolver): List<JSONArrayPattern> = newBasedOn(pattern, row, resolver)
    override fun parse(value: String, resolver: Resolver): Value = parsedJSON(value) ?: throw ContractParseException("""Parsing as $javaClass but failed. Value: $value""")
}

fun newBasedOn(jsonPattern: List<Pattern>, row: Row, resolver: Resolver): List<JSONArrayPattern> {
    val values = jsonPattern.map { pattern -> pattern.newBasedOn(row, resolver) }
    return multipleValidValues(values).map { JSONArrayPattern(it) }
}

fun multipleValidValues(values: List<List<Pattern>>): List<List<Pattern>> {
    if(values.isEmpty())
        return listOf(emptyList())

    val value: List<Pattern> = values.last()
    val subLists = multipleValidValues(values.dropLast(1))

    return subLists.map { list -> list.plus(value) }
}

fun generate(jsonPattern: List<Pattern>, resolver: Resolver): List<Value> =
    jsonPattern.flatMap { pattern ->
        when(pattern) {
            is RepeatingPattern -> pattern.generate(resolver).list
            else -> listOf(pattern.generate(resolver))
        }}

internal fun generateMultipleValues(pattern: Pattern, resolver: Resolver): List<Value> =
    0.until(randomNumber(10)).map { pattern.generate(resolver) }

internal fun randomNumber(max: Int) = Random().nextInt(max - 1) + 1
