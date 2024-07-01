package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.mismatchResult
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.NumberValue
import `in`.specmatic.core.value.Value
import java.math.BigDecimal
import java.security.SecureRandom
import java.util.*

data class NumberPattern(
    override val typeAlias: String? = null,
    val minLength: Int = 1,
    val maxLength: Int = Int.MAX_VALUE,
    val minimum: BigDecimal = LOWEST_DECIMAL,
    val exclusiveMinimum: Boolean = false,
    val maximum: BigDecimal = HIGHEST_DECIMAL,
    val exclusiveMaximum: Boolean = false,
    override val example: String? = null,
    val isDoubleFormat: Boolean = true
) : Pattern, ScalarType, HasDefaultExample {
    companion object {
        val BIG_DECIMAL_INC: BigDecimal = BigDecimal(Double.MIN_VALUE)
        val LOWEST_DECIMAL = BigDecimal("-1E+1000")
        val HIGHEST_DECIMAL = BigDecimal("1E+1000")
    }

    init {
        require(minLength > 0) { "minLength cannot be less than 1" }
        require(minLength <= maxLength) { "maxLength cannot be less than minLength" }
        if (exclusiveMinimum || exclusiveMaximum)
            require(minimum < maximum) { "Inappropriate minimum and maximum values set" }
        require(minimum <= maximum) { "Inappropriate minimum and maximum values set" }
    }

    private val smallestIncValue: BigDecimal
        get() = if (isDoubleFormat) BIG_DECIMAL_INC else BigDecimal(1)
    private val largestValue: BigDecimal
        get() = if (isDoubleFormat) BigDecimal(Double.MAX_VALUE) else Int.MAX_VALUE.toBigDecimal()

    private fun eval(a: BigDecimal, operator: String, b: BigDecimal): Boolean {
        return when (operator) {
            ">" -> a > b
            ">=" -> a >= b
            "<" -> a < b
            "<=" -> a <= b
            else -> throw IllegalArgumentException("Unsupported operator")
        }
    }

    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if (sampleData !is NumberValue)
            return mismatchResult("number", sampleData, resolver.mismatchMessages)

        if (sampleData.toStringLiteral().length < minLength)
            return mismatchResult("number with minLength $minLength", sampleData, resolver.mismatchMessages)

        if (sampleData.toStringLiteral().length > maxLength)
            return mismatchResult("number with maxLength $maxLength", sampleData, resolver.mismatchMessages)

        val sampleNumber = BigDecimal(sampleData.number.toString())

        val minOp = if (exclusiveMinimum) ">" else ">="
        if (!eval(sampleNumber, minOp, minimum))
            return mismatchResult("number $minOp $minimum", sampleData, resolver.mismatchMessages)

        val maxOp = if (exclusiveMaximum) "<" else "<="
        if (!eval(sampleNumber, maxOp, maximum))
            return mismatchResult("number $maxOp $maximum", sampleData, resolver.mismatchMessages)

        return Result.Success()
    }

    override fun generate(resolver: Resolver): Value {
        if (minAndMaxValuesNotSet())
            return resolver.resolveExample(example, this) ?: NumberValue(randomNumber(minLength))

        val min = if (minimum == LOWEST_DECIMAL) {
            if (maximum < smallestIncValue)
                maximum - BigDecimal(1)
            else
                smallestIncValue
        } else
            minimum
        val max = if (maximum == HIGHEST_DECIMAL) largestValue else maximum
        if (isDoubleFormat) return NumberValue(SecureRandom().nextDouble(min.toDouble(), max.toDouble()))
        return NumberValue(SecureRandom().nextInt(min.toInt(), max.toInt()))
    }

    private fun randomNumber(minLength: Int): Int {
        val first = randomPositiveDigit().toString()
        val rest = (1 until minLength).joinToString("") { randomDigit() }
        val stringNumber = "$first$rest"
        return stringNumber.toInt()
    }

    private fun randomDigit() = SecureRandom().nextInt(10).toString()

    private fun randomPositiveDigit() = (SecureRandom().nextInt(9) + 1)

    override fun newBasedOn(row: Row, resolver: Resolver): Sequence<Pattern> {
        return newBasedOnR(row, resolver).map { it.value }
    }

    override fun newBasedOnR(row: Row, resolver: Resolver): Sequence<ReturnValue<Pattern>> {
        val values = mutableListOf<HasValue<Pattern>>()

        val messageForTestFromThisObject =
            if(minAndMaxValuesNotSet())
                ""
            else
                "value within bounds"

        values.add(HasValue(this, messageForTestFromThisObject))

        if (minValueIsSet()) {
            if(exclusiveMinimum)
                values.add(HasValue(ExactValuePattern(NumberValue(minimum + smallestIncValue)), "value just within exclusive minimum $minimum"))
            else
                values.add(HasValue(ExactValuePattern(NumberValue(minimum)), "minimum value $minimum"))
        }

        if (maxValueIsSet()) {
            if(exclusiveMaximum)
                values.add(HasValue(ExactValuePattern(NumberValue(maximum - smallestIncValue)), "value just within exclusive maximum $maximum"))
            else
                values.add(HasValue(ExactValuePattern(NumberValue(maximum)), "maximum value $maximum"))
        }

        return values.asSequence()
    }

    private fun minValueIsSet() = minimum != LOWEST_DECIMAL
    private fun maxValueIsSet() = maximum != HIGHEST_DECIMAL
    private fun minAndMaxValuesNotSet() = minimum == LOWEST_DECIMAL && maximum == HIGHEST_DECIMAL

    override fun newBasedOn(resolver: Resolver): Sequence<Pattern> = sequenceOf(this)

    override fun negativeBasedOn(row: Row, resolver: Resolver): Sequence<ReturnValue<Pattern>> {
        val dataTypeNegatives: Sequence<Pattern> = sequenceOf(NullPattern, BooleanPattern(), StringPattern())
        val negativeForMinimumValue: Sequence<ReturnValue<Pattern>> =
            negativeRangeValues(minValueIsSet(), minimum - smallestIncValue, "value less than minimum of $minimum")

        val negativeForMaximumValue: Sequence<ReturnValue<Pattern>> =
            negativeRangeValues(maxValueIsSet(), maximum + smallestIncValue, "value greater than maximum of $maximum")

        return scalarAnnotation(this, dataTypeNegatives) + negativeForMinimumValue + negativeForMaximumValue
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
