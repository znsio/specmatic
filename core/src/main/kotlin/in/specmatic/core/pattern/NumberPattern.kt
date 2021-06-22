package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.mismatchResult
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.NumberValue
import `in`.specmatic.core.value.Value
import org.apache.commons.lang3.RandomStringUtils

data class NumberPattern(
    override val typeAlias: String? = null,
    val minLength: Int? = null,
    val maxLength: Int? = null
) : Pattern, ScalarType {
    init {
        require(minLength?.let { minLength > 0 } ?: true) {"minLength cannot be less than 1"}
        require(minLength?.let { maxLength?.let { minLength <= maxLength } }
            ?: true) { "maxLength cannot be less than minLength" }
    }

    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        return when (sampleData is NumberValue) {
            true -> {
                if (minLength != null && sampleData.toStringValue().length < minLength) return mismatchResult(
                    "number with minLength $minLength",
                    sampleData
                )
                if (maxLength != null && sampleData.toStringValue().length > maxLength) return mismatchResult(
                    "number with maxLength $maxLength",
                    sampleData
                )
                return Result.Success()
            }
            false -> mismatchResult("number", sampleData)
        }
    }

    override fun generate(resolver: Resolver): Value = NumberValue(randomNumber(minLength ?: 3))

    private fun randomNumber(minLength: Int) = RandomStringUtils.randomNumeric(minLength, minLength + 1).toInt()
    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> = listOf(this)
    override fun newBasedOn(resolver: Resolver): List<Pattern> = listOf(this)
    override fun parse(value: String, resolver: Resolver): Value {
        return NumberValue(convertToNumber(value))
    }

    override fun encompasses(
        otherPattern: Pattern,
        thisResolver: Resolver,
        otherResolver: Resolver,
        typeStack: TypeStack
    ): Result {
        return encompasses(this, otherPattern, thisResolver, otherResolver, typeStack)
    }

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value {
        return JSONArrayValue(valueList)
    }

    override val typeName: String = "number"

    override val pattern: Any = "(number)"
    override fun toString(): String = pattern.toString()
}

fun encompasses(
    thisPattern: Pattern,
    otherPattern: Pattern,
    thisResolver: Resolver,
    otherResolver: Resolver,
    typeStack: TypeStack
): Result =
    when {
        otherPattern::class == thisPattern::class -> Result.Success()
        otherPattern is ExactValuePattern -> otherPattern.fitsWithin(
            thisPattern.patternSet(thisResolver),
            otherResolver,
            thisResolver,
            typeStack
        )
        else -> mismatchResult(thisPattern, otherPattern)
    }
