package run.qontract.core.pattern

import io.cucumber.messages.Messages
import run.qontract.core.*
import run.qontract.core.utilities.mapZip
import run.qontract.core.value.*
import run.qontract.test.ContractTestException

fun rowsToTabularPattern(rows: List<Messages.GherkinDocument.Feature.TableRow>) =
        TabularPattern(rows.map { it.cellsList }.map { (key, value) ->
            key.value to toJSONValue(value.value)
        }.toMap())

fun toJSONValue(value: String): Pattern {
    return value.trim().let {
        val asNumber: Number? = try { convertToNumber(value) } catch (e: ContractParseException) { null }

        when {
            asNumber != null -> ExactMatchPattern(NumberValue(asNumber))
            it.startsWith("\"") && it.endsWith("\"") ->
                ExactMatchPattern(StringValue(it.removeSurrounding("\"")))
            it == "null" -> ExactMatchPattern(NullValue)
            it == "true" -> ExactMatchPattern(BooleanValue(true))
            it == "false" -> ExactMatchPattern(BooleanValue(false))
            else -> parsedPattern(value)
        }
    }
}

fun convertToNumber(value: String): Number {
    value.trim().let {
        try {
            return it.toInt()
        } catch (ignored: Exception) {
        }
        try {
            return it.toBigInteger()
        } catch (ignored: Exception) {
        }
        try {
            return it.toFloat()
        } catch (ignored: Exception) {
        }
        try {
            return it.toDouble()
        } catch (ignored: Exception) {
        }

        throw ContractParseException("Couldn't convert $value to number")
    }
}

class TabularPattern(override val pattern: Map<String, Pattern>) : Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if(sampleData !is JSONObjectValue)
            return Result.Failure("Expected: JSONObjectValue. Actual: ${sampleData?.javaClass ?: "null"}")

        val missingKey = pattern.keys.find { key -> isMissingKey(sampleData.jsonObject, key) }
        if(missingKey != null)
            return Result.Failure("Missing key $missingKey in ${sampleData.jsonObject}")

        mapZip(pattern, sampleData.jsonObject).forEach { (key, patternValue, sampleValue) ->
            when (val result = asPattern(patternValue, key).matches(sampleValue, withNumberTypePattern(resolver))) {
                is Result.Failure -> return result.add("Expected value at $key to match $patternValue, actual value $sampleValue in JSONObject ${sampleData.jsonObject}")
            }
        }

        return Result.Success()
    }

    override fun generate(resolver: Resolver) =
            JSONObjectValue(pattern.mapKeys { entry -> withoutOptionality(entry.key) }.mapValues { (key, pattern) ->
                when {
                    resolver.factStore.has(key) && resolver.factStore.get(key) != True ->
                        pattern.parse(resolver.factStore.get(key).toString(), resolver)
                    else -> asPattern(pattern, key).generate(resolver)
                }
            })

    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> =
        multipleValidKeys(pattern, row) { pattern ->
            newBasedOn(pattern, row, resolver)
        }.map { TabularPattern(it) }

    override fun parse(value: String, resolver: Resolver): Value = parsedJSON(value)
}

fun newBasedOn(patternMap: Map<String, Pattern>, row: Row, resolver: Resolver): List<Map<String, Pattern>> {
    val patternCollection = patternMap.mapValues { (key, pattern) ->
        val keyWithoutOptionality = withoutOptionality(key)

        when {
            row.containsField(keyWithoutOptionality) -> {
                val rowField = row.getField(keyWithoutOptionality)
                listOf(ExactMatchPattern(pattern.parse(rowField, resolver)))
            }
            else ->
                when (pattern) {
                    is LookupPattern -> pattern.copy(key=key)
                    else -> pattern
                }.newBasedOn(row, resolver)
        }
    }

    return patternList(patternCollection)
}

fun <ValueType> patternList(patternCollection: Map<String, List<ValueType>>): List<Map<String, ValueType>> {
    if(patternCollection.isEmpty())
        return listOf(emptyMap())

    val key = patternCollection.keys.first()

    return (patternCollection[key] ?: throw ContractTestException("key $key should not be empty in $patternCollection"))
            .flatMap { pattern ->
                val subLists = patternList<ValueType>(patternCollection - key)
                subLists.map { generatedPatternMap ->
                    generatedPatternMap.plus(Pair(key, pattern))
                }
            }
}

fun <ValueType> multipleValidKeys(patternMap: Map<String, ValueType>, row: Row, creator: (Map<String, ValueType>) -> List<Map<String, ValueType>>): List<Map<String, ValueType>> =
    keySets(patternMap.keys.toList(), row).map { keySet ->
        patternMap.filterKeys { key -> key in keySet }
    }.map { newPattern ->
        creator(newPattern)
    }.flatten()

internal fun keySets(listOfKeys: List<String>, row: Row): List<List<String>> {
    if(listOfKeys.isEmpty())
        return listOf(listOfKeys)

    val key = listOfKeys.last()
    val subLists = keySets(listOfKeys.dropLast(1), row)

    return subLists.flatMap { subList ->
        when {
            !row.containsField(key) && isOptional(key) -> listOf(subList, subList + key)
            else -> listOf(subList + key)
        }
    }
}
