package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.mismatchResult
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value

data class DictionaryPattern(val keyPattern: Pattern, val valuePattern: Pattern, override val typeAlias: String? = null) : Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if(sampleData !is JSONObjectValue)
            return mismatchResult("JSON object", sampleData, resolver.mismatchMessages)

        sampleData.jsonObject.forEach { (key, value) ->
            try {
                val parsedKey = keyPattern.parse(key, resolver)
                val result = resolver.matchesPattern(null, keyPattern, parsedKey)
                if (result is Result.Failure) {
                    return result.breadCrumb(key)
                }
            } catch(e: ContractException) {
                return e.failure().breadCrumb(key)
            }

            try {
                val parsedValue = when (value) {
                    is StringValue -> valuePattern.parse(value.string, resolver)
                    else -> value
                }

                val result = resolver.matchesPattern(null, valuePattern, parsedValue)
                if (result is Result.Failure) {
                    return result.breadCrumb(key)
                }
            } catch(e: ContractException) {
                return e.failure().breadCrumb(key)
            }
        }

        return Result.Success()
    }

    override fun generate(resolver: Resolver): Value {
        return JSONObjectValue((1..randomNumber(RANDOM_NUMBER_CEILING)).fold(emptyMap()) { obj, _ ->
            val key = resolver.withCyclePrevention(keyPattern, keyPattern::generate).toStringLiteral()
            val value = resolver.withCyclePrevention(valuePattern, valuePattern::generate)

            obj.plus(key to value)
        })
    }

    override fun newBasedOn(row: Row, resolver: Resolver): Sequence<ReturnValue<Pattern>> {
        val newValuePatterns = resolver.withCyclePrevention(valuePattern) { cyclePreventedResolver ->
            valuePattern.newBasedOn(Row(), cyclePreventedResolver)
        }

        return newValuePatterns.map {
            it.ifValue {
                DictionaryPattern(keyPattern, it)
            }
        }
    }

    override fun newBasedOn(resolver: Resolver): Sequence<Pattern> {
        val newValuePatterns = resolver.withCyclePrevention(valuePattern) { cyclePreventedResolver ->
            valuePattern.newBasedOn(cyclePreventedResolver)
        }

        return newValuePatterns.map {
            DictionaryPattern(keyPattern, it)
        }
    }

    override fun negativeBasedOn(row: Row, resolver: Resolver): Sequence<ReturnValue<Pattern>> {
        return sequenceOf(HasValue(this))
    }

    override fun parse(value: String, resolver: Resolver): Value = parsedJSONObject(value)

    override fun encompasses(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver, typeStack: TypeStack): Result =
            when (otherPattern) {
                is ExactValuePattern -> otherPattern.fitsWithin(listOf(this), otherResolver, thisResolver, typeStack)
                !is DictionaryPattern -> Result.Failure("Expected dictionary type, got ${otherPattern.typeName}")
                else -> {
                    listOf(
                            { biggerEncompassesSmaller(this.keyPattern, otherPattern.keyPattern, thisResolver, otherResolver, typeStack) },
                            { biggerEncompassesSmaller(this.valuePattern, otherPattern.valuePattern, thisResolver, otherResolver, typeStack) }
                    ).asSequence().map { it.invoke() }.firstOrNull { it is Result.Failure } ?: Result.Success()
                }
            }

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value {
        return JSONArrayValue(valueList)
    }

    override val typeName: String = "object with key type ${keyPattern.typeName} and value type ${valuePattern.typeName}"

    override val pattern: Any = "(${withoutPatternDelimiters(keyPattern.pattern.toString())}:${withoutPatternDelimiters(valuePattern.pattern.toString())})"
}
