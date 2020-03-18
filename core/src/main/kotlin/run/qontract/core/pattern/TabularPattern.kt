package run.qontract.core.pattern

import run.qontract.core.ContractParseException
import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.Value
import io.cucumber.messages.Messages

fun flatZipPatternValue(map1: Map<String, Pattern>, map2: Map<String, Any?>): List<Triple<String, Pattern, Value>> {
    return map1.filterKeys { key -> containsKey(map2, key) }.map { entry ->
        Triple(cleanupKey(entry.key), entry.value, asValue(lookupValue(map2, entry.key)))
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
            when (val result = asPattern(pattern, key).matches(sampleValue, resolver)) {
                is Result.Failure -> return result.add("Expected: $pattern Actual: ${sampleData.jsonObject}")
            }
        }

        return Result.Success()
    }

    override fun generate(resolver: Resolver) =
            JSONObjectValue(rows.mapKeys { entry -> cleanupKey(entry.key) }.mapValues { (key, pattern) ->
                if(resolver.serverStateMatch.contains(key)) {
                    val stateValue = resolver.serverStateMatch.get(key)
                    when(val result = pattern.matches(asValue(resolver.serverStateMatch.get(key)), resolver)) {
                        is Result.Failure -> throw ContractParseException("Server state $stateValue didn't match pattern ${pattern.pattern}")
                        else -> stateValue
                    }
                } else {
                    asPattern(pattern, key).generate(resolver).value
                }
            }.toMutableMap())

    override fun newBasedOn(row: Row, resolver: Resolver) = listOf(TabularPattern(newBasedOn(rows, row, resolver)))

    override val pattern: Any = rows
}

fun newBasedOn(jsonPattern: Map<String, Pattern>, row: Row, resolver: Resolver): Map<String, Pattern> =
    jsonPattern.mapValues { (key, pattern) ->
        val cleanKey = cleanupKey(key)
        when {
            pattern is LazyPattern -> pattern.copy(key=key).newBasedOn(row, resolver).first()
            row.containsField(cleanKey) -> when {
                isPrimitivePattern(pattern.pattern) -> ExactMatchPattern(parsePrimitive(pattern.pattern.toString(), row.getField(cleanKey).toString()))
                else -> ExactMatchPattern(row.getField(key) ?: "")
            }
            else -> pattern
        }
    }
