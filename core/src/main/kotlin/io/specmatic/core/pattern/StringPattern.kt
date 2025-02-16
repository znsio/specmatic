package io.specmatic.core.pattern

import com.mifmif.common.regex.Generex
import dk.brics.automaton.RegExp
import io.ktor.http.*
import io.specmatic.core.Resolver
import io.specmatic.core.Result
import io.specmatic.core.mismatchResult
import io.specmatic.core.pattern.config.NegativePatternConfiguration
import io.specmatic.core.utilities.runWithTimeout
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.StringValue
import io.specmatic.core.value.Value
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.TimeoutException

data class StringPattern (
    override val typeAlias: String? = null,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    override val example: String? = null,
    val regex: String? = null
) : Pattern, ScalarType, HasDefaultExample {
    val validRegex get() = regex?.let { validateRegex(replaceRegexLowerBounds(it)).removePrefix("^").removeSuffix("$") }
    private val effectiveMinLength get() = minLength ?: 0

    init {
        if (effectiveMinLength < 0) {
            throw IllegalArgumentException("minLength $effectiveMinLength cannot be less than 0")
        }
        maxLength?.let {
            if (effectiveMinLength > it) {
                throw IllegalArgumentException("maxLength $it cannot be less than minLength $effectiveMinLength")
            }
        }
        validRegex?.let {
            regexMinLengthValidation(it)
            regexMaxLengthValidation(it)
        }
    }

    private fun replaceRegexLowerBounds(regex: String): String {
        val pattern = Regex("""\{,(\d+)}""")
        return regex.replace(pattern) { matchResult ->
            "{0,${matchResult.groupValues[1]}}"
        }
    }

    private fun validateRegex(regex: String): String {
        if (regex.startsWith("/") && regex.endsWith("/")) {
            throw IllegalArgumentException("Invalid String Constraints - Regex $regex is not valid. OpenAPI follows ECMA-262 regular expressions, which do not support / / delimiters like those used in many programming languages")
        }
        return runCatching { RegExp(regex); regex }.getOrElse {
                e -> throw IllegalArgumentException("Failed to parse regex ${regex.quote()}\nReason: ${e.message}")
        }
    }

    private fun regexMinLengthValidation(regex: String) {
        minLength?.let {
            try {
                val someValue = runWithTimeout(1000) {
                    Generex(regex).random(it)
                }
                if (someValue.length < it) {
                    throw IllegalArgumentException("Invalid String Constraints - minLength $it cannot be greater than the length of longest possible string that matches regex $regex")
                }
            } catch (e: TimeoutException) {
                throw IllegalArgumentException("Invalid String Constraints - minLength $it cannot be greater than the length of longest possible string that matches regex $regex")
            }
        }
    }

    private fun regexMaxLengthValidation(regex: String) {
        maxLength?.let {
            val automaton = RegExp(regex).toAutomaton()
            val shortestPossibleLengthOfRegex = automaton.getShortestExample(true).length
            if (shortestPossibleLengthOfRegex > it) {
                throw IllegalArgumentException("Invalid String Constraints - maxLength $it cannot be less than the length of shortest possible string that matches regex $regex")
            }
        }
    }

    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if (sampleData?.hasTemplate() == true)
            return Result.Success()

        if (sampleData !is StringValue)
            return mismatchResult("string", sampleData, resolver.mismatchMessages)

        if (lengthBelowLowerBound(sampleData)) return mismatchResult(
            "string with minLength $effectiveMinLength",
            sampleData, resolver.mismatchMessages
        )

        if (lengthAboveUpperBound(sampleData)) return mismatchResult(
            "string with maxLength $maxLength",
            sampleData, resolver.mismatchMessages
        )

        if (doesNotMatchRegex(sampleData)) {
            return mismatchResult(
                """string that matches regex /$validRegex/""",
                sampleData,
                resolver.mismatchMessages
            )
        }

        return Result.Success()
    }

    private fun doesNotMatchRegex(sampleData: StringValue) =
        validRegex?.let { !Regex(it).matches(sampleData.toStringLiteral()) } ?: false

    private fun lengthAboveUpperBound(sampleData: StringValue) =
        maxLength?.let { sampleData.toStringLiteral().length > it } ?: false

    private fun lengthBelowLowerBound(sampleData: StringValue) =
        sampleData.toStringLiteral().length < effectiveMinLength

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

    private val patternBaseLength: Int =
        when {
            5 < effectiveMinLength -> effectiveMinLength
            maxLength != null && 5 > maxLength -> maxLength
            else -> 5
        }

    override fun generate(resolver: Resolver): Value {
        val defaultExample = resolver.resolveExample(example, this)

        defaultExample?.let {
            val result = matches(it, resolver)
            result.throwOnFailure()
            return it
        }

        return validRegex?.let {
            StringValue(generateFromRegex(it, effectiveMinLength, maxLength))
        } ?: StringValue(randomString(patternBaseLength))
    }

    private fun generateFromRegex(regex: String, minLength: Int, maxLength: Int? = null): String {
        return try {
            if (maxLength == null) {
                Generex(regex).random(minLength)
            } else {
                Generex(regex).random(minLength, maxLength)
            }
        } catch (e: StackOverflowError) {
            //TODO: This is a workaround for a bug in Generex. Remove this when the bug is fixed.
            generateFromRegex(regex, minLength, maxLength)
        }
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
            if (minLength != null && minLength != 0) {
                val pattern = copy(
                    minLength = effectiveMinLength.dec(),
                    maxLength = effectiveMinLength.dec(),
                    regex = null
                )
                yield(
                    HasValue(
                        pattern, "length lesser than minLength '$effectiveMinLength'"
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
