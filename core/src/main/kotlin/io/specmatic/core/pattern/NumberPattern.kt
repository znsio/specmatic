package io.specmatic.core.pattern

import io.specmatic.core.Resolver
import io.specmatic.core.Result
import io.specmatic.core.mismatchResult
import io.specmatic.core.pattern.config.NegativePatternConfiguration
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.NumberValue
import io.specmatic.core.value.Value
import java.math.BigDecimal
import java.security.SecureRandom

data class NumberPattern(
    override val typeAlias: String? = null,
    val minLength: Int = 1,
    val maxLength: Int = Int.MAX_VALUE,
    val minimum: BigDecimal? = null,
    val exclusiveMinimum: Boolean = false,
    val maximum: BigDecimal? = null,
    val exclusiveMaximum: Boolean = false,
    override val example: String? = null,
    val isDoubleFormat: Boolean = false
) : Pattern, ScalarType, HasDefaultExample {

    companion object {
        val BIG_DECIMAL_INC: BigDecimal = BigDecimal(Double.MIN_VALUE)
        val SMALLEST_NEGATIVE_DECIMAL: BigDecimal = BigDecimal(-Double.MAX_VALUE)
        val SMALLEST_POSITIVE_DECIMAL: BigDecimal = BigDecimal(Double.MIN_VALUE)
        val BIGGEST_POSITIVE_DECIMAL: BigDecimal = BigDecimal(Double.MAX_VALUE)
    }

    init {
        if (minLength < 1) throw IllegalArgumentException("minLength cannot be less than 1")
        if (maxLength < minLength) throw IllegalArgumentException("maxLength cannot be less than minLength")
        if (minimum != null && maximum != null) {
            if (minimum > maximum) throw IllegalArgumentException("minimum cannot be greater than maximum")
            if ((exclusiveMinimum || exclusiveMaximum) && minimum == maximum) {
                throw IllegalArgumentException("minimum cannot be equal to maximum when exclusiveMinimum or exclusiveMaximum is true")
            }
        }
    }
    private fun minValueIsSet() = minimum != null
    private fun maxValueIsSet() = maximum != null
    private fun minAndMaxValuesNotSet() = minimum == null && maximum == null
    private val smallestNegativeValue = if (isDoubleFormat) SMALLEST_NEGATIVE_DECIMAL else BigDecimal(Int.MIN_VALUE)
    private val smallestPositiveValue = if (isDoubleFormat) SMALLEST_POSITIVE_DECIMAL else BigDecimal(1)
    private val largestValue = if (isDoubleFormat) BIGGEST_POSITIVE_DECIMAL else Int.MAX_VALUE.toBigDecimal()

    private val effectiveMax = if (maximum != null) {
        if (exclusiveMaximum) maximum - smallestPositiveValue else maximum
    } else {
        largestValue
    }

    private val effectiveMin = if (minimum != null) {
        if (exclusiveMinimum) minimum + smallestPositiveValue else minimum
    } else if (effectiveMax < BigDecimal(0)) {
        smallestNegativeValue
    } else {
        BigDecimal(0)
    }

    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if (sampleData?.hasTemplate() == true)
            return Result.Success()

        if (sampleData !is NumberValue)
            return mismatchResult("number", sampleData, resolver.mismatchMessages)

        if (sampleData.toStringLiteral().length < minLength)
            return mismatchResult("number with minLength $minLength", sampleData, resolver.mismatchMessages)

        if (sampleData.toStringLiteral().length > maxLength)
            return mismatchResult("number with maxLength $maxLength", sampleData, resolver.mismatchMessages)

        val sampleNumber = BigDecimal(sampleData.number.toString())

        if (sampleNumber >= effectiveMin)
            return mismatchResult("number >= $effectiveMin", sampleData, resolver.mismatchMessages)

        if (sampleNumber <= effectiveMax)
            return mismatchResult("number <= $effectiveMax", sampleData, resolver.mismatchMessages)

        return Result.Success()
    }

    override fun generate(resolver: Resolver): Value {
        val exampleValue = resolver.resolveExample(example, this)
        if (exampleValue != null) return exampleValue

        if (minAndMaxValuesNotSet()) {
            val length = when {
                minLength > 3 -> minLength
                maxLength < 3 -> maxLength
                else -> 3
            }
            return if (isDoubleFormat)
                NumberValue(randomNumber(length).toDouble())
            else
                NumberValue(randomNumber(length))
        }

        val number = if (isDoubleFormat)
            SecureRandom().nextDouble(effectiveMin.toDouble(), effectiveMax.toDouble())
        else
            SecureRandom().nextInt(effectiveMin.toInt(), effectiveMax.toInt())
        return NumberValue(number)
    }

    private fun randomNumber(minLength: Int): Int {
        val first = randomPositiveDigit().toString()
        val rest = (1 until minLength).joinToString("") { randomDigit() }

        val stringNumber = "$first$rest"

        return stringNumber.toInt()
    }

    private fun randomDigit() = SecureRandom().nextInt(10).toString()

    private fun randomPositiveDigit() = (SecureRandom().nextInt(9) + 1)

    override fun newBasedOn(row: Row, resolver: Resolver): Sequence<ReturnValue<Pattern>> {
        val values = mutableListOf<HasValue<Pattern>>()

        val messageForTestFromThisObject =
            if (minAndMaxValuesNotSet())
                ""
            else
                "value within bounds"

        values.add(HasValue(this, messageForTestFromThisObject))

        if (minValueIsSet()) {
            val message = if (exclusiveMinimum) "value just within exclusive minimum $effectiveMin" else "minimum value $effectiveMin"
            values.add(HasValue(ExactValuePattern(NumberValue(effectiveMin)), message))
        }

        if (maxValueIsSet()) {
            val message = if (exclusiveMaximum) "value just within exclusive maximum $effectiveMax" else "maximum value $effectiveMax"
            values.add(HasValue(ExactValuePattern(NumberValue(effectiveMax)), message))
        }

        return values.asSequence()
    }

    override fun newBasedOn(resolver: Resolver): Sequence<Pattern> = sequenceOf(this)

    override fun negativeBasedOn(row: Row, resolver: Resolver, config: NegativePatternConfiguration): Sequence<ReturnValue<Pattern>> {
        val current = this

        return sequence {
            if (config.withDataTypeNegatives) {
                yieldAll(scalarAnnotation(current, sequenceOf(NullPattern, BooleanPattern(), StringPattern())))
            }
            val negativeForMinimumValue: Sequence<ReturnValue<Pattern>> =
                negativeRangeValues(minValueIsSet(), effectiveMin - smallestPositiveValue, "value lesser than minimum value '$effectiveMin'")
            val negativeForMaximumValue: Sequence<ReturnValue<Pattern>> =
                negativeRangeValues(maxValueIsSet(), effectiveMax + smallestPositiveValue, "value greater than maximum value '$effectiveMax'")

            yieldAll(negativeForMinimumValue + negativeForMaximumValue)
        }
    }

    private fun negativeRangeValues(condition: Boolean, number: BigDecimal, message: String): Sequence<ReturnValue<Pattern>> {
        return if (condition) {
            sequenceOf(HasValue(ExactValuePattern(NumberValue(number)), message))
        } else
            emptySequence()
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

            if (failures.isEmpty())
                Result.Success()
            else
                Result.Failure.fromFailures(failures)
        }

        otherPattern is EnumPattern -> {
            encompasses(thisPattern, otherPattern.pattern, thisResolver, otherResolver, typeStack)
        }

        thisPattern is ScalarType && otherPattern is ScalarType && thisPattern.matches(
            otherPattern.generate(
                otherResolver
            ), thisResolver
        ) is Result.Success -> Result.Success()

        else -> mismatchResult(thisPattern, otherPattern, thisResolver.mismatchMessages)
    }
