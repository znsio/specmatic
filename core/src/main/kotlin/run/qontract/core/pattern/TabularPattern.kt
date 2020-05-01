package run.qontract.core.pattern

import io.cucumber.messages.Messages
import run.qontract.core.*
import run.qontract.core.utilities.mapZip
import run.qontract.core.value.*

fun rowsToTabularPattern(rows: List<Messages.GherkinDocument.Feature.TableRow>) =
        TabularPattern(rows.map { it.cellsList }.map { (key, value) ->
            key.value to toJSONPattern(value.value)
        }.toMap())

fun toJSONPattern(value: String): Pattern {
    return value.trim().let {
        val asNumber: Number? = try { convertToNumber(value) } catch (e: Throwable) { null }

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

fun isNumber(value: StringValue): Boolean {
    return try {
        convertToNumber(value.string)
        true
    } catch(e: ContractException) {
        false
    }
}

fun convertToNumber(value: String): Number {
    value.trim().let {
        try {
            return it.toInt()
        } catch (ignored: Exception) {
        }
        try {
            return it.toLong()
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

        throw ContractException("""Couldn't convert "$value" to number""")
    }
}

class TabularPattern(override val pattern: Map<String, Pattern>) : Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if(sampleData !is JSONObjectValue)
            return mismatchResult("JSON object", sampleData)

        val missingKey = resolver.findMissingKey(pattern, sampleData.jsonObject)
        if(missingKey != null)
            return Result.Failure("Missing key $missingKey")

        mapZip(pattern, sampleData.jsonObject).forEach { (key, patternValue, sampleValue) ->
            when (val result = withNumberTypePattern(resolver).matchesPattern(key, patternValue, sampleValue)) {
                is Result.Failure -> return result.breadCrumb(key)
            }
        }

        return Result.Success()
    }

    override fun generate(resolver: Resolver) =
            JSONObjectValue(pattern.mapKeys { entry -> withoutOptionality(entry.key) }.mapValues { (key, pattern) ->
                attempt(breadCrumb = key) { resolver.generate(key, pattern) }
            })

    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> =
        multipleValidKeys(pattern, row) { pattern ->
            newBasedOn(pattern, row, resolver)
        }.map { TabularPattern(it) }

    override fun parse(value: String, resolver: Resolver): Value = parsedJSONStructure(value)
    override fun matchesPattern(pattern: Pattern, resolver: Resolver): Boolean = pattern is TabularPattern
    override val displayName: String = "json object"
}

fun newBasedOn(patternMap: Map<String, Pattern>, row: Row, resolver: Resolver): List<Map<String, Pattern>> {
    val patternCollection = patternMap.mapValues { (key, pattern) ->
        attempt(breadCrumb = key) {
            val keyWithoutOptionality = withoutOptionality(key)

            when {
                row.containsField(keyWithoutOptionality) -> {
                    val rowValue = row.getField(keyWithoutOptionality)
                    if(isPatternToken(rowValue)) {
                        val rowPattern = resolver.getPattern(rowValue)
                        attempt(breadCrumb = key) {
                            if (!pattern.matchesPattern(rowPattern, resolver))
                                throw ContractException("In the scenario, $key contained a ${pattern.displayName}, but the corresponding example contained ${rowPattern.displayName}")
                        }

                        rowPattern.newBasedOn(row, resolver)
                    } else {
                        attempt("Format error in example of \"$keyWithoutOptionality\"") { listOf(ExactMatchPattern(pattern.parse(rowValue, resolver))) }
                    }
                }
                else ->
                    when (pattern) {
                        is DeferredPattern -> pattern.copy(key = key)
                        else -> pattern
                    }.newBasedOn(row, resolver)
            }
        }
    }

    return patternList(patternCollection)
}

fun <ValueType> patternList(patternCollection: Map<String, List<ValueType>>): List<Map<String, ValueType>> {
    if(patternCollection.isEmpty())
        return listOf(emptyMap())

    val key = patternCollection.keys.first()

    return (patternCollection[key] ?: throw ContractException("key $key should not be empty in $patternCollection"))
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
            row.containsField(withoutOptionality(key)) -> listOf(subList + key)
            isOptional(key) -> listOf(subList, subList + key)
            else -> listOf(subList + key)
        }
    }
}
