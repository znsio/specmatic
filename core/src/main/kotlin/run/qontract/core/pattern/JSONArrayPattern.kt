package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.breadCrumb
import run.qontract.core.utilities.stringTooPatternArray
import run.qontract.core.value.*
import java.util.*

data class JSONArrayPattern(override val pattern: List<Pattern> = emptyList()) : Pattern, EncompassableList {
    override fun getEncompassableList(count: Int, resolver: Resolver): List<Pattern> {
        if(count > pattern.size && pattern.last() !is RestPattern)
            throw ContractException("The lengths of the expected and actual array patterns don't match.")

        if(count > pattern.size) {
            if(pattern.indexOfFirst { it is RestPattern } < pattern.lastIndex)
                throw ContractException("A rest operator ... can only be used in the last entry of an array.")

            val missingEntryCount = count - pattern.size
            val missingEntries = 0.until(missingEntryCount).map { pattern.last() }
            val paddedPattern = pattern.plus(missingEntries)
            return _getEncompassableList(paddedPattern, resolver)
        }

        return getEncompassableList(resolver)
    }

    override fun isEndless(): Boolean = pattern.last() is RestPattern

    fun getEncompassableList(resolver: Resolver): List<Pattern> {
        return _getEncompassableList(pattern, resolver)
    }

    private fun _getEncompassableList(pattern: List<Pattern>, resolver: Resolver): List<Pattern> {
        return pattern.mapIndexed { index, patternEntry ->
            when (patternEntry) {
                !is RestPattern -> resolvedHop(patternEntry, resolver)
                else -> resolvedHop(patternEntry.pattern, resolver)
            }
        }
    }

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
        if(otherPattern !is EncompassableList)
            return Result.Failure("Expected array or list, got ${otherPattern.typeName}")

        if(otherPattern.isEndless() && !this.isEndless())
            return Result.Failure("Finite list is not a superset of an infinite list.")

        return try {
            val otherEncompassables = otherPattern.getEncompassableList(pattern.size, otherResolver)
            val encompassables = if (otherEncompassables.size > pattern.size) getEncompassableList(otherEncompassables.size, thisResolver) else getEncompassableList(thisResolver)

            val results = encompassables.asSequence().zip(otherEncompassables.asSequence()).mapIndexed { index, (bigger, smaller) ->
                Pair(index, bigger.encompasses2(smaller, thisResolver, otherResolver))
            }

            results.find { it.second is Result.Success }?.second ?: results.firstOrNull()?.let { result -> result.second.breadCrumb("[${result.first}]") } ?: Result.Success()
        } catch (e: ContractException) {
            return Result.Failure(e.report())
        }
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
