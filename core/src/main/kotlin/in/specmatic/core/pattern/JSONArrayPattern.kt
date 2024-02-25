package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.mismatchResult
import `in`.specmatic.core.utilities.stringTooPatternArray
import `in`.specmatic.core.utilities.withNullPattern
import `in`.specmatic.core.utilities.withNumberType
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.ListValue
import `in`.specmatic.core.value.Value
import java.util.*

data class JSONArrayPattern(override val pattern: List<Pattern> = emptyList(), override val typeAlias: String? = null) : Pattern, SequenceType {
    override val memberList: MemberList
        get() {
            if (pattern.isEmpty())
                return MemberList(emptyList(), null)

            if (pattern.indexOfFirst { it is RestPattern }.let { it >= 0 && it < pattern.lastIndex })
                throw ContractException("A rest operator ... can only be used in the last entry of an array.")

            return pattern.last().let { last ->
                when (last) {
                    is RestPattern -> MemberList(pattern.dropLast(1), last.pattern)
                    else -> MemberList(pattern, null)
                }
            }
        }

    constructor(jsonString: String, typeAlias: String?) : this(stringTooPatternArray(jsonString), typeAlias = typeAlias)

    @Throws(Exception::class)
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if (sampleData !is JSONArrayValue)
            return mismatchResult(this, sampleData, resolver.mismatchMessages)

        if (sampleData.list.isEmpty() && (pattern.isEmpty()))
            return Result.Success()

        val resolverWithNumberType = withNumberType(withNullPattern(resolver))
        val resolvedTypes = pattern.map { resolvedHop(it, resolverWithNumberType) }

        if(resolvedTypes.singleOrNull() is ListPattern) {
            return resolvedTypes.single().matches(sampleData, resolver)
        }

        return resolvedTypes.asSequence().mapIndexed { index, patternValue ->
            when {
                patternValue is RestPattern -> {
                    val rest = when (index) {
                        sampleData.list.size -> emptyList()
                        else -> sampleData.list.slice(index..sampleData.list.lastIndex)
                    }
                    patternValue.matches(JSONArrayValue(rest), resolverWithNumberType).breadCrumb("[$index...${sampleData.list.lastIndex}]")
                }
                index >= sampleData.list.size ->
                    Result.Failure("Expected an array of length ${pattern.size}, actual length ${sampleData.list.size}")
                else -> {
                    val sampleValue = sampleData.list[index]
                    resolverWithNumberType.matchesPattern(null, patternValue, sampleValue).breadCrumb("""[$index]""")
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

    override fun newBasedOn(row: Row, resolver: Resolver): Sequence<JSONArrayPattern> {
        val resolverWithNullType = withNullPattern(resolver)
        return newBasedOn(pattern, row, resolverWithNullType).map { JSONArrayPattern(it) }
    }

    override fun newBasedOn(resolver: Resolver): Sequence<JSONArrayPattern> {
        val resolverWithNullType = withNullPattern(resolver)
        return newBasedOn(pattern, resolverWithNullType).map { JSONArrayPattern(it) }
    }

    override fun negativeBasedOn(row: Row, resolver: Resolver): Sequence<Pattern> = sequenceOf(NullPattern).let {
        if(pattern.size == 1)
            it.plus(pattern[0])
        else
            it
    }

    override fun parse(value: String, resolver: Resolver): Value = parsedJSONArray(value, resolver.mismatchMessages)
    override fun encompasses(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver, typeStack: TypeStack): Result {
        val thisResolverWithNullType = withNullPattern(thisResolver)
        val otherResolverWithNullType = withNullPattern(otherResolver)

        return when (otherPattern) {
            is ExactValuePattern -> otherPattern.fitsWithin(listOf(this), otherResolverWithNullType, thisResolverWithNullType, typeStack)
            is SequenceType -> try {
                val otherMembers = otherPattern.memberList
                val theseMembers = this.memberList

                validateInfiniteLength(otherMembers, theseMembers).ifSuccess {
                    val otherEncompassables = otherMembers.getEncompassableList(pattern.size, otherResolverWithNullType)
                    val encompassables = when {
                        otherEncompassables.size > pattern.size -> theseMembers.getEncompassableList(otherEncompassables.size, thisResolverWithNullType)
                        else -> memberList.getEncompassables(thisResolverWithNullType)
                    }

                    val results = encompassables.zip(otherEncompassables).mapIndexed { index, (bigger, smaller) ->
                        ResultWithIndex(index, biggerEncompassesSmaller(bigger, smaller, thisResolverWithNullType, otherResolverWithNullType, typeStack))
                    }

                    results.find {
                        it.result is Result.Failure
                    }?.let { result ->
                        result.result.breadCrumb("[${result.index}]")
                    } ?: Result.Success()
                }
            } catch (e: ContractException) {
                Result.Failure(e.report())
            }
            else -> Result.Failure("Expected array or list, got ${otherPattern.typeName}")
        }
    }

    private fun validateInfiniteLength(otherMembers: MemberList, theseMembers: MemberList): Result = when {
        otherMembers.isEndless() && !theseMembers.isEndless() -> Result.Failure("Finite list is not a superset of an infinite list.")
        else -> Result.Success()
    }

    override val typeName: String = "json array"
}

fun newBasedOn(patterns: List<Pattern>, row: Row, resolver: Resolver): Sequence<List<Pattern>> {
    val values = patterns.mapIndexed { index, pattern ->
        attempt(breadCrumb = "[$index]") {
            resolver.withCyclePrevention(pattern) { cyclePreventedResolver ->
                pattern.newBasedOn(row, cyclePreventedResolver)
            }
        }
    }

    return listCombinations(values)
}

fun newBasedOn(patterns: List<Pattern>, resolver: Resolver): Sequence<List<Pattern>> {
    val values = patterns.mapIndexed { index, pattern ->
        attempt(breadCrumb = "[$index]") {
            resolver.withCyclePrevention(pattern) { cyclePreventedResolver ->
                pattern.newBasedOn(cyclePreventedResolver)
            }
        }
    }

    return listCombinations(values)
}

fun listCombinations(values: List<Sequence<Pattern?>>): Sequence<List<Pattern>> {
    if (values.isEmpty())
        return sequenceOf(emptyList())

    val lastValueTypes: Sequence<Pattern?> = values.last()
    val subLists = listCombinations(values.dropLast(1))

    return subLists.flatMap { subList ->
        lastValueTypes.map { lastValueType ->
            if (lastValueType != null)
                subList.plus(lastValueType)
            else
                subList
        }
    }
}

fun <ValueType> allOrNothingListCombinations(values: List<List<ValueType?>>): Sequence<List<ValueType>> {
    if (values.isEmpty())
        return sequenceOf(emptyList())

    val iterators = values.filter {
        it.any()
    }.map {
        it.iterator()
    }

    val first = mutableListOf<ValueType?>()
    val ranOut = iterators.map { false }.toMutableList()

    var oneRoundDone = false

    return sequence {
        while(true) {
            val nextValue = iterators.mapIndexed { index, iterator ->
                val nextValueFromIterator = if (iterator.hasNext()) {
                    val value = iterator.next()

                    if(!oneRoundDone)
                        first.add(value)

                    value
                } else {
                    ranOut[index] = true
                    first[index]
                }

                nextValueFromIterator
            }

            if (ranOut.all { it }) {
                break
            }

            oneRoundDone = true

            yield(nextValue.filterNotNull())
        }

    }
}

private fun keyCombinations(values: List<List<Pattern?>>,
                            optionalSelector: (List<Pattern?>) -> Pattern?): Sequence<Pattern?> {
    return values.map {
        optionalSelector(it)
    }.asSequence().filterNotNull()
}

fun generate(jsonPattern: List<Pattern>, resolver: Resolver): List<Value> =
        jsonPattern.mapIndexed { index, pattern ->
            resolver.withCyclePrevention(pattern) { cyclePreventedResolver ->
                when (pattern) {
                    is RestPattern -> attempt(breadCrumb = "[$index...${jsonPattern.lastIndex}]") {
                        val list = pattern.generate(cyclePreventedResolver) as ListValue
                        list.list
                    }

                    else -> attempt(breadCrumb = "[$index]") { listOf(pattern.generate(cyclePreventedResolver)) }
                }
            }
        }.flatten()

const val RANDOM_NUMBER_CEILING = 10

internal fun randomNumber(max: Int) = Random().nextInt(max - 1) + 1
