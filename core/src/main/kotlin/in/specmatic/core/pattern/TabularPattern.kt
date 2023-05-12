package `in`.specmatic.core.pattern

import `in`.specmatic.core.*
import `in`.specmatic.core.utilities.mapZip
import `in`.specmatic.core.utilities.stringToPatternMap
import `in`.specmatic.core.utilities.withNullPattern
import `in`.specmatic.core.value.*
import io.cucumber.messages.types.TableRow

fun toTabularPattern(jsonContent: String, typeAlias: String? = null): TabularPattern =
    toTabularPattern(stringToPatternMap(jsonContent), typeAlias)

fun toTabularPattern(map: Map<String, Pattern>, typeAlias: String? = null): TabularPattern {
    val missingKeyStrategy: UnexpectedKeyCheck = when ("...") {
        in map -> IgnoreUnexpectedKeys
        else -> ValidateUnexpectedKeys
    }

    return TabularPattern(map.minus("..."), missingKeyStrategy, typeAlias)
}

data class TabularPattern(
    override val pattern: Map<String, Pattern>,
    private val unexpectedKeyCheck: UnexpectedKeyCheck = ValidateUnexpectedKeys,
    override val typeAlias: String? = null
) : Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if (sampleData !is JSONObjectValue)
            return mismatchResult("JSON object", sampleData, resolver.mismatchMessages)

        val resolverWithNullType = withNullPattern(resolver)

        val keyErrors: List<Result.Failure> =
            resolverWithNullType.findKeyErrorList(pattern, sampleData.jsonObject).map {
                it.missingKeyToResult("key", resolver.mismatchMessages).breadCrumb(it.name)
            }

        val results: List<Result.Failure> =
            mapZip(pattern, sampleData.jsonObject).map { (key, patternValue, sampleValue) ->
                resolverWithNullType.matchesPattern(key, patternValue, sampleValue).breadCrumb(key)
            }.filterIsInstance<Result.Failure>()

        val failures = keyErrors.plus(results)

        return if (failures.isEmpty())
            Result.Success()
        else
            Result.Failure.fromFailures(failures)
    }

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value = JSONArrayValue(valueList)

    override fun generate(resolver: Resolver): JSONObjectValue {
        val resolverWithNullType = withNullPattern(resolver)
        return JSONObjectValue(pattern.mapKeys { entry -> withoutOptionality(entry.key) }.mapValues { (key, pattern) ->
            attempt(breadCrumb = key) { resolverWithNullType.withCyclePrevention(pattern) {it.generate(key, pattern)} }
        })
    }

    override fun generateWithAll(resolver: Resolver): Value {
        return attempt(breadCrumb = "HEADERS") {
            JSONObjectValue(pattern.filterNot { it.key == "..." }.mapKeys {
                attempt(breadCrumb = it.key) {
                    withoutOptionality(it.key)
                }
            }.mapValues {
                it.value.generateWithAll(resolver)
            })
        }
    }
    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> {
        val resolverWithNullType = withNullPattern(resolver)
        return allOrNothingCombinationIn(pattern, if(resolver.generativeTestingEnabled) Row() else row) { pattern ->
            newBasedOn(pattern, row, resolverWithNullType)
        }.map {
            toTabularPattern(it.mapKeys { (key, _) ->
                withoutOptionality(key)
            })
        }
    }

    override fun newBasedOn(resolver: Resolver): List<Pattern> {
        val resolverWithNullType = withNullPattern(resolver)
        val allOrNothingCombinationIn = allOrNothingCombinationIn(pattern) { pattern ->
            newBasedOn(pattern, resolverWithNullType)
        }
        return allOrNothingCombinationIn.map { toTabularPattern(it) }
    }

    override fun negativeBasedOn(row: Row, resolver: Resolver): List<Pattern> {
        return this.newBasedOn(row, resolver)
    }

    override fun parse(value: String, resolver: Resolver): Value = parsedJSONObject(value, resolver.mismatchMessages)
    override fun encompasses(
        otherPattern: Pattern,
        thisResolver: Resolver,
        otherResolver: Resolver,
        typeStack: TypeStack
    ): Result {
        val thisResolverWithNullType = withNullPattern(thisResolver)
        val otherResolverWithNullType = withNullPattern(otherResolver)

        return when (otherPattern) {
            is ExactValuePattern -> otherPattern.fitsWithin(
                listOf(this),
                otherResolverWithNullType,
                thisResolverWithNullType,
                typeStack
            )
            is TabularPattern -> mapEncompassesMap(
                pattern,
                otherPattern.pattern,
                thisResolverWithNullType,
                otherResolverWithNullType,
                typeStack
            )
            is JSONObjectPattern -> mapEncompassesMap(
                pattern,
                otherPattern.pattern,
                thisResolverWithNullType,
                otherResolverWithNullType,
                typeStack
            )
            else -> Result.Failure("Expected json type, got ${otherPattern.typeName}")
        }
    }

    override val typeName: String = "json object"
}

fun newBasedOn(patternMap: Map<String, Pattern>, row: Row, resolver: Resolver): List<Map<String, Pattern>> {
    val patternCollection = patternMap.mapValues { (key, pattern) ->
        attempt(breadCrumb = key) {
            newBasedOn(row, key, pattern, resolver)
        }
    }

    return patternList(patternCollection)
}

fun negativeBasedOn(patternMap: Map<String, Pattern>, row: Row, resolver: Resolver): List<Map<String, Pattern>> {
    val eachKeyMappedToPatternMap = patternMap.mapValues { patternMap }
    val negativePatternsMap = patternMap.mapValues { (_, pattern) -> pattern.negativeBasedOn(row, resolver) }
    val modifiedPatternMap: Map<String, List<Map<String, List<Pattern>>>> = eachKeyMappedToPatternMap.mapValues { (keyToNegate, patterns) ->
        val negativePatterns = negativePatternsMap[keyToNegate]
        negativePatterns!!.map { negativePattern ->
            patterns.mapValues { (key, pattern) ->
                attempt(breadCrumb = key) {
                    when (key == keyToNegate) {
                        true ->
                            attempt(breadCrumb = "Setting $key to $negativePattern for negative test scenario") {
                                newBasedOn(Row(), key, negativePattern, resolver)
                            }
                        else -> newBasedOn(row, key, pattern, resolver)
                    }
                }
            }
        }
    }
    return modifiedPatternMap.values.map { list: List<Map<String, List<Pattern>>> ->
        list.toList().map { patternList(it) }.flatten()
    }.flatten()
}

fun newBasedOn(patternMap: Map<String, Pattern>, resolver: Resolver): List<Map<String, Pattern>> {
    val patternCollection = patternMap.mapValues { (key, pattern) ->
        attempt(breadCrumb = key) {
            newBasedOn(key, pattern, resolver)
        }
    }

    return patternValues(patternCollection)
}

fun newBasedOn(row: Row, key: String, pattern: Pattern, resolver: Resolver): List<Pattern> {
    val keyWithoutOptionality = key(pattern, key)

    return when {
        row.containsField(keyWithoutOptionality) -> {
            val rowValue = row.getField(keyWithoutOptionality)

            if (isPatternToken(rowValue)) {
                val rowPattern = resolver.getPattern(rowValue)

                attempt(breadCrumb = key) {
                    when (val result = pattern.encompasses(rowPattern, resolver, resolver)) {
                        is Result.Success -> {
                            resolver.withCyclePrevention(rowPattern, isOptional(key)) { cyclePreventedResolver ->
                                rowPattern.newBasedOn(row, cyclePreventedResolver)
                            }?:
                            // Handle cycle (represented by null value) by using empty list for optional properties
                            listOf()
                        }
                        is Result.Failure -> throw ContractException(result.toFailureReport())
                    }
                }
            } else {
                val parsedRowValue = attempt("Format error in example of \"$keyWithoutOptionality\"") {
                    resolver.parse(pattern, rowValue)
                }

                when (val matchResult = resolver.matchesPattern(null, pattern, parsedRowValue)) {
                    is Result.Failure -> throw ContractException(matchResult.toFailureReport())
                    else -> listOf(ExactValuePattern(parsedRowValue))
                }
            }
        }
        else -> resolver.withCyclePrevention(pattern, isOptional(key)) { cyclePreventedResolver ->
            pattern.newBasedOn(row, cyclePreventedResolver)
        }?:
        // Handle cycle (represented by null value) by using empty list for optional properties
        listOf()
    }
}

fun newBasedOn(key: String, pattern: Pattern, resolver: Resolver): List<Pattern> {
    return resolver.withCyclePrevention(pattern, isOptional(key)) { cyclePreventedResolver ->
        pattern.newBasedOn(cyclePreventedResolver)
    }?:
    // Handle cycle (represented by null value) by using empty list for optional properties
    listOf()
}

fun key(pattern: Pattern, key: String): String {
    return withoutOptionality(
        when (pattern) {
            is Keyed -> pattern.key ?: key
            else -> key
        }
    )
}

fun <ValueType> patternList(patternCollection: Map<String, List<ValueType>>): List<Map<String, ValueType>> {
    if (patternCollection.isEmpty())
        return listOf(emptyMap())

    val spec = CombinationSpec(patternCollection, Flags.maxTestRequestCombinations())
    return spec.selectedCombinations;
}

fun <ValueType> patternValues(patternCollection: Map<String, List<ValueType>>): List<Map<String, ValueType>> {
    if (patternCollection.isEmpty())
        return listOf(emptyMap())

    val maxKeyValues = patternCollection.map { (_, value) -> value.size }.maxOrNull() ?: 0

    return (0 until maxKeyValues).map {
        keyCombinations(patternCollection) { key, value ->
            when {
                value.size > it -> key to value[it]
                else -> key to value[0]
            }
        }
    }.toList()
}

private fun <ValueType> keyCombinations(
    patternCollection: Map<String, List<ValueType>>,
    optionalSelector: (String, List<ValueType>) -> Pair<String, ValueType>
): Map<String, ValueType> {
    return patternCollection
        .filterValues { it.isNotEmpty() }
        .map { (key, value) ->
        optionalSelector(key, value)
    }.toMap()
}

fun <ValueType> forEachKeyCombinationIn(
    patternMap: Map<String, ValueType>,
    row: Row,
    creator: (Map<String, ValueType>) -> List<Map<String, ValueType>>
): List<Map<String, ValueType>> =
    keySets(patternMap.keys.toList(), row).map { keySet ->
        patternMap.filterKeys { key -> key in keySet }
    }.map { newPattern ->
        creator(newPattern)
    }.flatten()

fun <ValueType> allOrNothingCombinationIn(
    patternMap: Map<String, ValueType>,
    row: Row = Row(),
    creator: (Map<String, ValueType>) -> List<Map<String, ValueType>>
): List<Map<String, ValueType>> {
    val keyLists = if (patternMap.keys.any { isOptional(it) }) {
        val nothingList: Set<String> = patternMap.keys.filter { k -> !isOptional(k) || row.containsField(withoutOptionality(k)) }.toSet()
        val allList: Set<String> = patternMap.keys

        listOf(allList, nothingList).distinct()
    } else {
        listOf(patternMap.keys)
    }

    val keySets: List<Map<String, ValueType>> = keyLists.map { keySet ->
        patternMap.filterKeys { key -> key in keySet }
    }

    val keySetValues: List<List<Map<String, ValueType>>> = keySets.map { newPattern ->
        creator(newPattern)
    }

    val flatten: List<Map<String, ValueType>> = keySetValues.flatten()

    return flatten
}

internal fun keySets(listOfKeys: List<String>, row: Row): List<List<String>> {
    if (listOfKeys.isEmpty())
        return listOf(listOfKeys)

    val key = listOfKeys.last()
    val subLists = keySets(listOfKeys.dropLast(1), row)

    return subLists.flatMap { subList ->
        when {
            row.containsField(withoutOptionality(key)) -> listOf(subList + key)
            isOptional(key) -> listOf(subList, subList + key)
            else -> listOf(subList + key)
        }
    }
}

fun rowsToTabularPattern(rows: List<TableRow>, typeAlias: String? = null) =
    toTabularPattern(rows.map { it.cells }.associate { (key, value) ->
        key.value to toJSONPattern(value.value)
    }, typeAlias)

fun toJSONPattern(value: String): Pattern {
    return value.trim().let {
        val asNumber: Number? = try {
            convertToNumber(value)
        } catch (e: Throwable) {
            null
        }

        when {
            asNumber != null -> ExactValuePattern(NumberValue(asNumber))
            it.startsWith("\"") && it.endsWith("\"") ->
                ExactValuePattern(StringValue(it.removeSurrounding("\"")))
            it == "null" -> ExactValuePattern(NullValue)
            it == "true" -> ExactValuePattern(BooleanValue(true))
            it == "false" -> ExactValuePattern(BooleanValue(false))
            else -> parsedPattern(value)
        }
    }
}

fun isNumber(value: StringValue): Boolean {
    return try {
        convertToNumber(value.string)
        true
    } catch (e: ContractException) {
        false
    }
}

fun convertToNumber(value: String): Number = value.trim().let {
    stringToInt(it) ?: stringToLong(it) ?: stringToFloat(it) ?: stringToDouble(it)
    ?: throw ContractException("""Expected number, actual was "$value"""")
}

internal fun stringToInt(value: String): Int? = try {
    value.toInt()
} catch (e: Throwable) {
    null
}

internal fun stringToLong(value: String): Long? = try {
    value.toLong()
} catch (e: Throwable) {
    null
}

internal fun stringToFloat(value: String): Float? = try {
    value.toFloat()
} catch (e: Throwable) {
    null
}

internal fun stringToDouble(value: String): Double? = try {
    value.toDouble()
} catch (e: Throwable) {
    null
}
