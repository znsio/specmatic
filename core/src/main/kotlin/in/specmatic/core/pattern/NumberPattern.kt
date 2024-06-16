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
    val minLength: Int = 1,
    val maxLength: Int = Int.MAX_VALUE,
    val minimum: Double = Double.NEGATIVE_INFINITY,
    val exclusiveMinimum: Boolean = false,
    val maximum: Double = Double.POSITIVE_INFINITY,
    val exclusiveMaximum: Boolean = false,
    override val example: String? = null
) : Pattern, ScalarType, HasDefaultExample {
    init {
        require(minLength > 0) { "minLength cannot be less than 1" }
        require(minLength <= maxLength) { "maxLength cannot be less than minLength" }
        val countOfExclusivesSet = listOf(exclusiveMinimum, exclusiveMaximum).count { it }
        require(maximum - minimum >= countOfExclusivesSet) { "Inappropriate minimum and maximum values set" }
    }

    private fun eval(a: Double, operator: String, b: Double): Boolean {
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

        val sampleNumber = sampleData.number.toDouble()

        val minOp = if (exclusiveMinimum) ">" else ">="
        if (!eval(sampleNumber,  minOp, minimum))
            return mismatchResult("number $minOp $minimum", sampleData, resolver.mismatchMessages)

        val maxOp = if (exclusiveMaximum) "<" else "<="
        if (!eval(sampleNumber,  maxOp, maximum))
            return mismatchResult("number $maxOp $maximum", sampleData, resolver.mismatchMessages)

        return Result.Success()
    }

    override fun generate(resolver: Resolver): Value =
        resolver.resolveExample(example, this) ?: NumberValue(randomNumber(minLength))

    private fun randomNumber(minLength: Int): Int {
        val first = randomPositiveDigit().toString()
        val rest = (1 until minLength).joinToString("") { randomDigit() }

        val stringNumber = "$first$rest"

        return stringNumber.toInt()
    }

    private fun randomDigit() = Random().nextInt(10).toString()

    private fun randomPositiveDigit() = (Random().nextInt(9) + 1)

    override fun newBasedOn(row: Row, resolver: Resolver): Sequence<Pattern> = sequenceOf(this)
    override fun newBasedOn(resolver: Resolver): Sequence<Pattern> = sequenceOf(this)

    override fun negativeBasedOn(row: Row, resolver: Resolver): Sequence<ReturnValue<Pattern>> {
        return scalarAnnotation(this, sequenceOf(NullPattern, BooleanPattern(), StringPattern()))
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
