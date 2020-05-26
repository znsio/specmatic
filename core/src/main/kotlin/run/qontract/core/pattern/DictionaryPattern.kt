package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.mismatchResult
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value

data class DictionaryPattern(val keyPattern: Pattern, val valuePattern: Pattern) : Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if(sampleData !is JSONObjectValue)
            return mismatchResult("JSON object", sampleData)

        val resolverWithNumericString = resolver.copy(newPatterns = resolver.newPatterns.plus("(number)" to NumericStringPattern))

        sampleData.jsonObject.forEach { (key, value) ->
            when(val result = resolverWithNumericString.matchesPattern(null, keyPattern, StringValue(key))) {
                is Result.Failure -> return result.breadCrumb(key)
            }

            when(val result = resolver.matchesPattern(null, valuePattern, value)) {
                is Result.Failure -> return result.breadCrumb("\"$key\"=${value.toStringValue()}")
            }
        }

        return Result.Success()
    }

    override fun generate(resolver: Resolver): Value {
        return JSONObjectValue((1..randomNumber(10)).fold(emptyMap()) { obj, _ ->
            val key = keyPattern.generate(resolver).toStringValue()
            val value = valuePattern.generate(resolver)

            obj.plus(key to value)
        })
    }

    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> {
        val newValuePatterns = valuePattern.newBasedOn(Row(), resolver)

        return newValuePatterns.map {
            DictionaryPattern(keyPattern, it)
        }
    }

    override fun parse(value: String, resolver: Resolver): Value = parsedValue(value)

    override fun encompasses(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver): Result =
            when (otherPattern) {
                is ExactValuePattern -> otherPattern.fitsWithin(listOf(this), otherResolver, thisResolver)
                !is DictionaryPattern -> Result.Failure("Expected dictionary type, got ${otherPattern.typeName}")
                else -> {
                    listOf(
                            { this.keyPattern.encompasses(otherPattern, thisResolver, otherResolver) },
                            { this.valuePattern.encompasses(otherPattern, thisResolver, otherResolver) }
                    ).asSequence().map { it.invoke() }.firstOrNull { it is Result.Failure } ?: Result.Success()
                }
            }

    override val typeName: String = "object with key type ${keyPattern.typeName} and value type ${valuePattern.typeName}"

    override val pattern: Any = "(${withoutPatternDelimiters(keyPattern.pattern.toString())}:${withoutPatternDelimiters(valuePattern.pattern.toString())})"
}
