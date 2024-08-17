package io.specmatic.core.pattern

import com.mifmif.common.regex.Generex
import io.specmatic.core.Resolver
import io.specmatic.core.Result
import io.specmatic.core.mismatchResult
import io.specmatic.core.pattern.config.NegativePatternConfiguration
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.StringValue
import io.specmatic.core.value.Value
import java.nio.charset.StandardCharsets
import java.util.*

data class StringPattern (
    override val typeAlias: String? = null,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    override val example: String? = null,
    val regex: String? = null
) : Pattern, ScalarType, HasDefaultExample {
    init {
        if (minLength != null && maxLength != null && minLength > maxLength) {
            throw IllegalArgumentException("maxLength cannot be less than minLength")
        }
    }

    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if (sampleData?.hasTemplate() == true)
            return Result.Success()

        return when (sampleData) {
            is StringValue -> {
                if (minLength != null && sampleData.toStringLiteral().length < minLength) return mismatchResult(
                    "string with minLength $minLength",
                    sampleData, resolver.mismatchMessages
                )

                if (maxLength != null && sampleData.toStringLiteral().length > maxLength) return mismatchResult(
                    "string with maxLength $maxLength",
                    sampleData, resolver.mismatchMessages
                )

                if(regex != null && !Regex(regex).matches(sampleData.toStringLiteral())) {
                    return mismatchResult(
                        """string that matches regex /$regex/""",
                        sampleData,
                        resolver.mismatchMessages
                    )
                }

                return Result.Success()
            }
            else -> mismatchResult("string", sampleData, resolver.mismatchMessages)
        }
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

    private val randomStringLength: Int =
        when {
            minLength != null && 5 < minLength -> minLength
            maxLength != null && 5 > maxLength -> maxLength
            else -> 5
        }
    
    override fun generate(resolver: Resolver): Value {
        val defaultExample: Value? = resolver.resolveExample(example, this)

        if (regex != null) {
            if(defaultExample == null)
                return StringValue(Generex(regex.removePrefix("^").removeSuffix("$")).random(randomStringLength))

            val defaultExampleMatchResult = matches(defaultExample, resolver)

            if(defaultExampleMatchResult.isSuccess())
                return defaultExample

            throw ContractException("Schema example ${defaultExample.toStringLiteral()} does not match pattern $regex")
        }

        if(defaultExample != null) {
            if(defaultExample !is StringValue)
                throw ContractException("Schema example ${defaultExample.toStringLiteral()} is not a string")

            return defaultExample
        }

        return StringValue(randomString(randomStringLength))
    }

    override fun newBasedOn(row: Row, resolver: Resolver): Sequence<ReturnValue<Pattern>> {
        val minLengthExample: ReturnValue<Pattern>? = minLength?.let {
            HasValue(ExactValuePattern(StringValue(randomString(it))), "minimum length string")
        }

        val withinRangeExample: ReturnValue<Pattern> = HasValue(this)

        val maxLengthExample: ReturnValue<Pattern>? = maxLength?.let {
            HasValue(ExactValuePattern(StringValue(randomString(it))), "maximum length string")
        }

        return sequenceOf(minLengthExample, withinRangeExample, maxLengthExample).filterNotNull()
    }

    override fun newBasedOn(resolver: Resolver): Sequence<Pattern> = sequenceOf(this)

    override fun negativeBasedOn(row: Row, resolver: Resolver, config: NegativePatternConfiguration): Sequence<ReturnValue<Pattern>> {
        val current = this

        return sequence {
            if (config.withDataTypeNegatives) {
                yieldAll(scalarAnnotation(current, sequenceOf(NullPattern, NumberPattern(), BooleanPattern())))
            }

            if (maxLength != null) {
                val pattern = copy(
                    minLength = maxLength.inc(),
                    maxLength = maxLength.inc(),
                    regex = null
                )
                yield(
                    HasValue(pattern, "length greater than maxLength '$maxLength'")
                )
            }
            if (minLength != null) {
                val pattern = copy(
                    minLength = minLength.dec(),
                    maxLength = minLength.dec(),
                    regex = null
                )
                yield(
                    HasValue(
                        pattern, "length lesser than minLength '$minLength'"
                    )
                )
            }
            if (regex != null) {
                val pattern = copy(regex = regex.plus("_"))
                yield(HasValue(pattern, "invalid regex"))
            }
        }
    }

    override fun parse(value: String, resolver: Resolver): Value = StringValue(value)
    override val typeName: String = "string"

    override val pattern: Any = "(string)"
    override fun toString(): String = pattern.toString()
}

fun randomString(length: Int = 5): String {
    val array = ByteArray(length)
    val random = Random()
    for (index in array.indices) {
        array[index] = (random.nextInt(25) + 65).toByte()
    }
    return String(array, StandardCharsets.UTF_8)
}
