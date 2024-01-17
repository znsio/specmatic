package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.mismatchResult
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.NumberValue
import `in`.specmatic.core.value.Value
import java.util.*

data class NumberPattern(
    override val typeAlias: String? = null,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    override val example: String? = null
) : Pattern, ScalarType, HasDefaultExample {
    init {
        require(minLength?.let { minLength > 0 } ?: true) {"minLength cannot be less than 1"}
        require(minLength?.let { maxLength?.let { minLength <= maxLength } }
            ?: true) { "maxLength cannot be less than minLength" }
    }

    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        return when (sampleData is NumberValue) {
            true -> {
                if (minLength != null && sampleData.toStringLiteral().length < minLength) return mismatchResult(
                    "number with minLength $minLength",
                    sampleData, resolver.mismatchMessages
                )
                if (maxLength != null && sampleData.toStringLiteral().length > maxLength) return mismatchResult(
                    "number with maxLength $maxLength",
                    sampleData, resolver.mismatchMessages
                )
                return Result.Success()
            }
            false -> mismatchResult("number", sampleData, resolver.mismatchMessages)
        }
    }

    override fun generate(resolver: Resolver): Value =
        resolver.resolveExample(example, this) ?:
            NumberValue(randomNumber(minLength ?: 3))

    private fun randomNumber(minLength: Int): Int {
        val first = randomPositiveDigit().toString()
        val rest = (1 until minLength).joinToString("") { randomDigit() }

        val stringNumber = "$first$rest"

        return stringNumber.toInt()
    }

    private fun randomDigit() = Random().nextInt(10).toString()

    private fun randomPositiveDigit() = (Random().nextInt(9) + 1)

    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> = listOf(this)
    override fun newBasedOn(resolver: Resolver): List<Pattern> = listOf(this)
    override fun negativeBasedOn(row: Row, resolver: Resolver): List<Pattern> {
        return listOf(NullPattern, BooleanPattern(), StringPattern())
    }

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
        otherPattern is AnyPattern -> {
            val failures: List<Result.Failure> = otherPattern.patternSet(otherResolver).map {
                thisPattern.encompasses(it, thisResolver, otherResolver)
            }.filterIsInstance<Result.Failure>()

            if(failures.isEmpty())
                Result.Success()
            else
                Result.Failure.fromFailures(failures)
        }
        otherPattern is EnumPattern -> {
            encompasses(thisPattern, otherPattern.pattern, thisResolver, otherResolver, typeStack)
        }
        else -> mismatchResult(thisPattern, otherPattern, thisResolver.mismatchMessages)
    }
