package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.breadCrumb
import run.qontract.core.utilities.stringTooPatternArray
import run.qontract.core.utilities.withNullPattern
import run.qontract.core.utilities.withNumberType
import run.qontract.core.value.JSONArrayValue
import run.qontract.core.value.ListValue
import run.qontract.core.value.Value
import java.util.*

data class JSONArrayPattern(override val pattern: List<Pattern> = emptyList(), override val typeAlias: String? = null) : Pattern, EncompassableList {
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

    override fun getEncompassableList(): MemberList {
        if(pattern.isEmpty())
            return MemberList(emptyList(), null)

        if(pattern.indexOfFirst { it is RestPattern }.let { it >= 0 && it < pattern.lastIndex})
            throw ContractException("A rest operator ... can only be used in the last entry of an array.")

        return pattern.last().let { last ->
            when (last) {
                is RestPattern -> MemberList(pattern.dropLast(1), last.pattern)
                else -> MemberList(pattern, null)
            }
        }
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

    constructor(jsonString: String, typeAlias: String?) : this(stringTooPatternArray(jsonString), typeAlias = typeAlias)

    @Throws(Exception::class)
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if(sampleData !is JSONArrayValue)
            return Result.Failure("Value is not a JSON array")

        if(sampleData.list.isEmpty())
            return Result.Success()

        val resolverWithNumberType = withNumberType(withNullPattern(resolver))

        val resolvedTypes = pattern.map { resolvedHop(it, resolverWithNumberType) }

        return resolvedTypes.mapIndexed { index, patternValue ->
            when {
                patternValue is RestPattern -> {
                    val rest = when (index) {
                        sampleData.list.size -> emptyList()
                        else -> sampleData.list.slice(index..sampleData.list.lastIndex)
                    }
                    patternValue.matches(JSONArrayValue(rest), resolverWithNumberType).breadCrumb("[$index...${sampleData.list.lastIndex}]")
                }
                index == sampleData.list.size ->
                    Result.Failure("Expected an array of length ${pattern.size}, actual length ${sampleData.list.size}")
                else -> {
                    val sampleValue = sampleData.list[index]
                    patternValue.matches(sampleValue, resolverWithNumberType).breadCrumb("""[$index]""")
                }
            }
        }.find {
            it is Result.Failure
        } ?: Result.Success()
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
    override fun parse(value: String, resolver: Resolver): Value = parsedJSON(value)
    override fun encompasses(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver, typeStack: TypeStack): Result {
        val thisResolverWithNullType = withNullPattern(thisResolver)
        val otherResolverWithNullType = withNullPattern(otherResolver)

        return when {
            otherPattern is ExactValuePattern -> otherPattern.fitsWithin(listOf(this), otherResolverWithNullType, thisResolverWithNullType, typeStack)
            otherPattern !is EncompassableList -> Result.Failure("Expected array or list, got ${otherPattern.typeName}")
            otherPattern.isEndless() && !this.isEndless() -> Result.Failure("Finite list is not a superset of an infinite list.")
            else -> try {
                val otherEncompassables = otherPattern.getEncompassableList().getEncompassableList(pattern.size, otherResolverWithNullType)
                val encompassables = if (otherEncompassables.size > pattern.size)
                    getEncompassableList().getEncompassableList(otherEncompassables.size, thisResolverWithNullType)
//                    getEncompassableList(otherEncompassables.size, thisResolverWithNullType)
                else
                    getEncompassableList().getEncompassables(thisResolverWithNullType)

                val results = encompassables.zip(otherEncompassables).mapIndexed { index, (bigger, smaller) ->
                    Pair(index, biggerEncompassesSmaller(bigger, smaller, thisResolverWithNullType, otherResolverWithNullType, typeStack))
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
