package run.qontract.core.pattern

import run.qontract.core.ContractParseException
import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.Value
import io.cucumber.messages.Messages
import run.qontract.test.ContractTestException

fun flatZipPatternValue(map1: Map<String, Pattern>, map2: Map<String, Any?>): List<Triple<String, Pattern, Value>> {
    return map1.filterKeys { key -> containsKey(map2, key) }.map { entry ->
        Triple(withoutOptionality(entry.key), entry.value, asValue(lookupValue(map2, entry.key)))
    }
}

fun rowsToPattern(rows: List<Messages.GherkinDocument.Feature.TableRow>) =
        TabularPattern(rows.map { it.cellsList }.map { (key, value) ->
            key.value to convertTabularValueToPattern(value.value, null)
        }.toMap())

fun convertTabularValueToPattern(value: String, key: String?) =
        Pair(value.trim(), value.trim().toLowerCase()).let { (trimmed, lowered) ->
            when {
                trimmed.isEmpty() -> NoContentPattern()
                isRepeatingPattern(trimmed) -> RepeatingPattern(trimmed)
                lowered in primitivePatterns -> primitivePatterns.getOrDefault(lowered, UnknownPattern())
                isPatternToken(trimmed) -> LazyPattern(trimmed, key)
                trimmed.startsWith("\"") && trimmed.endsWith("\"") -> ExactMatchPattern(trimmed.removeSurrounding("\""))
                lowered in listOf("true", "false") -> ExactMatchPattern(lowered.toBoolean())
                lowered == "null" -> NoContentPattern()
                else -> ExactMatchPattern(convertToNumber(trimmed))
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

class TabularPattern(private val rows: Map<String, Pattern>) : Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if(sampleData !is JSONObjectValue)
            return Result.Failure("Expected: JSONObjectValue. Actual: ${sampleData?.javaClass ?: "null"}")

        val missingKey = rows.keys.find { key -> isMissingKey(sampleData.jsonObject, key) }
        if(missingKey != null)
            return Result.Failure("Missing key $missingKey in ${sampleData.jsonObject}")

        val resolverWithNumberType = resolver.copy().also {
            it.addCustomPattern("(number)", NumberTypePattern())
        }

        flatZipPatternValue(rows, sampleData.jsonObject).forEach { (key, pattern, sampleValue) ->
            when (val result = asPattern(pattern, key).matches(sampleValue, resolverWithNumberType)) {
                is Result.Failure -> return result.add("Expected: $pattern Actual: ${sampleData.jsonObject}")
            }
        }

        return Result.Success()
    }

    override fun generate(resolver: Resolver) =
            JSONObjectValue(rows.mapKeys { entry -> withoutOptionality(entry.key) }.mapValues { (key, pattern) ->
                if(resolver.serverStateMatch.contains(key) && resolver.serverStateMatch.get(key) != true) {
                    val stateValue = resolver.serverStateMatch.get(key)
                    when(pattern.matches(asValue(resolver.serverStateMatch.get(key)), resolver)) {
                        is Result.Failure -> throw ContractParseException("Server state $stateValue didn't match pattern ${pattern.pattern}")
                        else -> stateValue
                    }
                } else {
                    asPattern(pattern, key).generate(resolver).value
                }
            }.toMutableMap())

    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> =
        multipleValidKeys(rows, row) { pattern ->
            newBasedOn(pattern, row, resolver)
        }.map { TabularPattern(it) }

    override fun parse(value: String, resolver: Resolver): Value = parsedJSON(value) ?: throw ContractParseException("""Parsing as $javaClass but failed. Value: $value""")

    override val pattern: Any = rows
}

fun newBasedOn(patternMap: Map<String, Pattern>, row: Row, resolver: Resolver): List<Map<String, Pattern>> {
    val patternCollection = patternMap.mapValues { (key, pattern) ->
        val cleanKey = withoutOptionality(key)
        when {
            pattern is LazyPattern -> pattern.copy(key=key).newBasedOn(row, resolver)
            row.containsField(cleanKey) -> when {
                isPrimitivePattern(pattern.pattern) -> listOf(ExactMatchPattern(parsePrimitive(pattern.pattern.toString(), row.getField(cleanKey).toString())))
                else -> listOf(ExactMatchPattern(row.getField(key) ?: ""))
            }
            else -> listOf(pattern)
        }
    }

    return patternList<Pattern>(patternCollection)
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
