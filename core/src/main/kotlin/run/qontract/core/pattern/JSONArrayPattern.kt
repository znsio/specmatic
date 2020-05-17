package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.utilities.stringTooPatternArray
import run.qontract.core.value.*
import java.util.*

data class JSONArrayPattern(override val pattern: List<Pattern> = emptyList()) : Pattern {
    constructor(jsonString: String) : this(stringTooPatternArray(jsonString))

    @Throws(Exception::class)
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if(sampleData !is JSONArrayValue)
            return Result.Failure("Value is not a JSON array")

        if(sampleData.list.isEmpty())
            return Result.Success()

        val resolverWithNumberType = resolver.copy(newPatterns = resolver.newPatterns.plus("(number)" to NumberTypePattern))

        val resolvedPattern = pattern.map {
            when(it) {
                is DeferredPattern -> it.resolvePattern(resolver)
                else -> it
            }
        }

        for (index in resolvedPattern.indices) {
            when(val patternValue = resolvedPattern[index]) {
                is RestPattern -> {
                    val rest = if(index == sampleData.list.size) emptyList() else sampleData.list.slice(index..sampleData.list.lastIndex)
                    return when (val result = patternValue.matches(JSONArrayValue(rest), resolver)) {
                        is Result.Failure -> result.breadCrumb("[$index...${sampleData.list.lastIndex}]")
                        else -> result
                    }
                }
                else -> {
                    if(index == sampleData.list.size)
                        return Result.Failure("Expected an array of length ${pattern.size}, actual length ${sampleData.list.size}")

                    val sampleValue = sampleData.list[index]
                    when (val result = patternValue.matches(sampleValue, resolverWithNumberType)) {
                        is Result.Failure -> return result.breadCrumb("""[$index]""")
                    }
                }
            }
        }

        return Result.Success()
    }

    override fun generate(resolver: Resolver): Value {
        return JSONArrayValue(generate(pattern, resolver))
    }

    override fun newBasedOn(row: Row, resolver: Resolver): List<JSONArrayPattern> = newBasedOn(pattern, row, resolver).map { JSONArrayPattern(it) }
    override fun parse(value: String, resolver: Resolver): Value = parsedJSONStructure(value)
    override fun encompasses(otherPattern: Pattern, resolver: Resolver): Boolean = otherPattern is JSONArrayPattern
    override fun encompasses2(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver): Result {
        if(otherPattern !is JSONArrayPattern)
            return Result.Failure("Expected array, got ${otherPattern.typeName}")

        val resolverWithNumberType = thisResolver.copy(newPatterns = thisResolver.newPatterns.plus("(number)" to NumberTypePattern))

        val resolvedPattern = pattern.map {
            when(it) {
                is DeferredPattern -> it.resolvePattern(thisResolver)
                else -> it
            }
        }

        for (index in resolvedPattern.indices) {
            when(val patternValue = resolvedPattern[index]) {
                is RestPattern -> {
                    val rest = if(index == otherPattern.pattern.size) emptyList() else otherPattern.pattern.slice(index..otherPattern.pattern.lastIndex)
                    return when (val result = patternValue.encompasses2(JSONArrayPattern(rest), thisResolver, otherResolver)) {
                        is Result.Failure -> result.breadCrumb("[$index...${otherPattern.pattern.lastIndex}]")
                        else -> result
                    }
                }
                else -> {
                    if(index == otherPattern.pattern.size)
                        return Result.Failure("Expected an array type of length ${pattern.size}, actual length ${otherPattern.pattern.size}")

                    val otherPatternItem = otherPattern.pattern[index]
                    when (val result = patternValue.encompasses2(otherPatternItem, resolverWithNumberType, otherResolver)) {
                        is Result.Failure -> return result.breadCrumb("""[$index]""")
                    }
                }
            }
        }

        return Result.Success()
    }

    override val typeName: String = "json array"
}

fun newBasedOn(jsonPattern: List<Pattern>, row: Row, resolver: Resolver): List<List<Pattern>> {
    val values = jsonPattern.mapIndexed { index, pattern ->
        attempt(breadCrumb = "[$index]") {
            pattern.newBasedOn(row, resolver)
        }
    }

    return multipleValidValues(values)
}

fun multipleValidValues(values: List<List<Pattern>>): List<List<Pattern>> {
    if(values.isEmpty())
        return listOf(emptyList())

    val value: List<Pattern> = values.last()
    val subLists = multipleValidValues(values.dropLast(1))

    return subLists.map { list -> list.plus(value) }
}

fun generate(jsonPattern: List<Pattern>, resolver: Resolver): List<Value> =
    jsonPattern.mapIndexed { index, pattern ->
        when (pattern) {
            is RestPattern -> attempt(breadCrumb = "[$index...${jsonPattern.lastIndex}]") { pattern.generate(resolver).list }
            else -> attempt(breadCrumb = "[$index]") { listOf(pattern.generate(resolver)) }
        }
    }.flatten()

internal fun randomNumber(max: Int) = Random().nextInt(max - 1) + 1
