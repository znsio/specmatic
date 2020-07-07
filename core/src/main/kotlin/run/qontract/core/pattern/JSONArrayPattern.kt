package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.breadCrumb
import run.qontract.core.utilities.stringTooPatternArray
import run.qontract.core.utilities.withNullPattern
import run.qontract.core.value.JSONArrayValue
import run.qontract.core.value.ListValue
import run.qontract.core.value.Value
import java.util.*

data class JSONArrayPattern(override val pattern: List<Pattern> = emptyList()) : Pattern, EncompassableList {
    override fun getEncompassableList(count: Int, resolver: Resolver): List<Pattern> {
        if(count > 0 && pattern.isEmpty())
            throw ContractException("The lengths of the expected and actual array patterns don't match.")

        val resolverWithNullType = withNullPattern(resolver)
        if(count > pattern.size && pattern.last() !is RestPattern)
            throw ContractException("The lengths of the expected and actual array patterns don't match.")

        if(count > pattern.size) {
            if(pattern.indexOfFirst { it is RestPattern } < pattern.lastIndex)
                throw ContractException("A rest operator ... can only be used in the last entry of an array.")

            val missingEntryCount = count - pattern.size
            val missingEntries = 0.until(missingEntryCount).map { pattern.last() }
            val paddedPattern = pattern.plus(missingEntries)
            return getEncompassableList(paddedPattern, resolverWithNullType)
        }

        return getEncompassableList(resolverWithNullType)
    }

    override fun isEndless(): Boolean = pattern.isNotEmpty() && pattern.last() is RestPattern

    fun getEncompassableList(resolver: Resolver): List<Pattern> {
        val resolverWithNullType = withNullPattern(resolver)
        return getEncompassableList(pattern, resolverWithNullType)
    }

    private fun getEncompassableList(pattern: List<Pattern>, resolver: Resolver): List<Pattern> {
        val resolverWithNullType = withNullPattern(resolver)
        return pattern.map { patternEntry ->
            when (patternEntry) {
                !is RestPattern -> resolvedHop(patternEntry, resolverWithNullType)
                else -> resolvedHop(patternEntry.pattern, resolverWithNullType)
            }
        }
    }

    constructor(jsonString: String) : this(stringTooPatternArray(jsonString))

    @Throws(Exception::class)
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if(sampleData !is JSONArrayValue)
            return Result.Failure("Value is not a JSON array")

        val resolverWithNullType = withNullPattern(resolver)
        if(sampleData.list.isEmpty())
            return Result.Success()

        val resolverWithNumberType = resolverWithNullType.copy(newPatterns = resolverWithNullType.newPatterns.plus("(number)" to NumberPattern))

        val resolvedTypes = pattern.map {
            when(it) {
                is DeferredPattern -> it.resolvePattern(resolverWithNullType)
                else -> it
            }
        }

        for (index in resolvedTypes.indices) {
            when(val patternValue = resolvedTypes[index]) {
                is RestPattern -> {
                    val rest = if(index == sampleData.list.size) emptyList() else sampleData.list.slice(index..sampleData.list.lastIndex)
                    return when (val result = patternValue.matches(JSONArrayValue(rest), resolverWithNullType)) {
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

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value {
        return JSONArrayValue(valueList)
    }

    override fun generate(resolver: Resolver): Value {
        val resolverWithNullType = withNullPattern(resolver)
        return JSONArrayValue(generate(pattern, resolverWithNullType))
    }

    override fun newBasedOn(row: Row, resolver: Resolver): List<JSONArrayPattern> {
        val resolverWithNullType = withNullPattern(resolver)
        return newBasedOn(pattern, row, resolverWithNullType).map { JSONArrayPattern(it) }
    }
    override fun parse(value: String, resolver: Resolver): Value = parsedJSONStructure(value)
    override fun encompasses(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver): Result {
        val thisResolverWithNullType = withNullPattern(thisResolver)
        val otherResolverWithNullType = withNullPattern(otherResolver)

        return when {
            otherPattern is ExactValuePattern -> otherPattern.fitsWithin(listOf(this), otherResolverWithNullType, thisResolverWithNullType)
            otherPattern !is EncompassableList -> Result.Failure("Expected array or list, got ${otherPattern.typeName}")
            otherPattern.isEndless() && !this.isEndless() -> Result.Failure("Finite list is not a superset of an infinite list.")
            else -> try {
                val otherEncompassables = otherPattern.getEncompassableList(pattern.size, otherResolverWithNullType)
                val encompassables = if (otherEncompassables.size > pattern.size) getEncompassableList(otherEncompassables.size, thisResolverWithNullType) else getEncompassableList(thisResolverWithNullType)

                val results = encompassables.zip(otherEncompassables).mapIndexed { index, (bigger, smaller) ->
                    Pair(index, bigger.encompasses(smaller, thisResolverWithNullType, otherResolverWithNullType))
                }

                results.find { it.second is Result.Failure }?.let { result -> result.second.breadCrumb("[${result.first}]") }
                        ?: Result.Success()
            } catch (e: ContractException) {
                Result.Failure(e.report())
            }
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

    return listCombinations(values)
}

fun listCombinations(values: List<List<Pattern>>): List<List<Pattern>> {
    if(values.isEmpty())
        return listOf(emptyList())

    val value: List<Pattern> = values.last()
    val subLists = listCombinations(values.dropLast(1))

    return subLists.flatMap { list ->
        value.map { type ->
            list.plus(type)
        }
    }
}

fun generate(jsonPattern: List<Pattern>, resolver: Resolver): List<Value> =
    jsonPattern.mapIndexed { index, pattern ->
        when (pattern) {
            is RestPattern -> attempt(breadCrumb = "[$index...${jsonPattern.lastIndex}]") {
                val list = pattern.generate(resolver) as ListValue
                list.list
            }
            else -> attempt(breadCrumb = "[$index]") { listOf(pattern.generate(resolver)) }
        }
    }.flatten()

internal fun randomNumber(max: Int) = Random().nextInt(max - 1) + 1
