package run.qontract.core.pattern

import run.qontract.core.ContractParseException
import run.qontract.core.Resolver
import run.qontract.core.utilities.jsonStringToArray
import run.qontract.core.Result
import run.qontract.core.value.*
import java.util.*

@Throws(Exception::class)
private fun matchesRepeating(pattern: Pattern, arraySample: List<Value>, startingIndex: Int, resolver: Resolver): Result {
    for (index in startingIndex until arraySample.size) {
        when (val result = asPattern(pattern, null).matches(arraySample[index], resolver)) {
            is Result.Failure -> return result.add("Expected array[$index] to match repeating pattern $pattern. Actual value: ${arraySample[index]} in array $arraySample")
        }
    }

    return Result.Success()
}

class JSONArrayPattern2 : Pattern {
    override val pattern = listOf<Value?>()

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

        val resolverWithNumberType = resolver.copy().also {
            it.addCustomPattern("(number)", NumberTypePattern())
        }

        for (index in 0 until pattern.size) {
            if(index == sampleData.list.size) return failed(sampleData)
            val sampleValue = sampleData.list[index]
            val patternValue = pattern[index]
            if (isRepeatingPattern(patternValue)) {
                when (val result = matchesRepeating(extractPatternFromRepeatingToken(patternValue as Any), sampleData.list, index, resolverWithNumberType)) {
                    is Result.Failure -> return result.add(failedMessage(sampleData))
                }
            } else {
                when (val result = asPattern(patternValue, null).matches(asValue(sampleValue?: NoValue()), resolverWithNumberType)) {
                    is Result.Failure -> return result.add("Expected array[$index] to match $patternValue. Actual value: $sampleValue in ${sampleData.list}")
                }
            }
        }

        return Result.Success()
    }

    override fun generate(resolver: Resolver): Value {
        return JSONArrayValue(generate(pattern, resolver))
    }

    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> = newBasedOn(pattern, row, resolver)
    override fun parse(value: String, resolver: Resolver): Value = parsedJSON(value) ?: throw ContractParseException("""Parsing as $javaClass but failed. Value: $value""")
}

fun newBasedOn(jsonPattern: List<Any?>, row: Row, resolver: Resolver): List<JSONArrayPattern> {
    val values = jsonPattern.map(::asValue).map { patternValue ->
        when(patternValue) {
            is StringValue ->
                when {
                    isLazyPattern(patternValue.string) -> LazyPattern(patternValue.string, null).newBasedOn(row, resolver).map { it.pattern }
                    else -> listOf(patternValue.string)
                }
            is JSONObjectValue -> newBasedOn(patternValue.jsonObject, row, resolver)
            is JSONArrayValue -> newBasedOn(patternValue.list, row, resolver)
            else -> listOf(patternValue.value)
        }
    }

    return multipleValidValues(values).map { JSONArrayPattern(it) }
}

fun multipleValidValues(values: List<List<Any?>>): List<List<Any?>> {
    if(values.isEmpty())
        return listOf(values)

    val value = values.takeLast(1)
    val subLists = multipleValidValues(values.dropLast(1))

    return subLists.map { list ->
        list + value
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
