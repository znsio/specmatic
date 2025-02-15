package io.specmatic.core.pattern

import com.mifmif.common.regex.Generex
import dk.brics.automaton.RegExp
import io.ktor.http.*
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
    private val effectiveMinLength = minLength ?: 0
    private val effectiveMaxLength = maxLength ?: (effectiveMinLength + 5)
    val validRegex = regex?.let { replaceRegexLowerBounds(it) }

    init {
        if (effectiveMinLength < 0) {
            throw IllegalArgumentException("minimumLength cannot be less than 0")
        }
        if (effectiveMinLength > effectiveMaxLength) {
            throw IllegalArgumentException("maximumLength cannot be less than minimumLength")
        }

        validRegex?.let {
            val regexWithoutCaretAndDollar = validateRegex(it).removePrefix("^").removeSuffix("$")
            regexMinLengthValidation(regexWithoutCaretAndDollar)
            regexMaxLengthValidation(regexWithoutCaretAndDollar)
        }
    }

    private fun replaceRegexLowerBounds(regex: String): String {
        val pattern = Regex("""\{,(\d+)}""")
        return regex.replace(pattern) { matchResult ->
            "{0,${matchResult.groupValues[1]}}"
        }
    }

    private fun validateRegex(regex: String): String {
        return runCatching { RegExp(regex); regex }.getOrElse {
                e -> throw IllegalArgumentException("Failed to parse regex ${regex.quote()}\nReason: ${e.message}")
        }
    }

    private fun regexMinLengthValidation(regex: String) {
        val matchedStrings = Generex(regex).getMatchedStrings(effectiveMinLength)
        if (matchedStrings.isEmpty()) {
            throw IllegalArgumentException("Invalid String Constraints - minLength cannot be greater than length of longest possible string that matches regex")
        }
    }

    private fun regexMaxLengthValidation(regex: String) {
        val automaton = RegExp(regex).toAutomaton()
        val shortestPossibleLengthOfRegex = automaton.getShortestExample(true).length
        if (shortestPossibleLengthOfRegex > effectiveMaxLength) {
            throw IllegalArgumentException("Invalid String Constraints - maxLength cannot be less than length of shortest possible string that matches regex")
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
            "string with maxLength $effectiveMaxLength",
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
        validRegex != null && !Regex(validRegex).matches(sampleData.toStringLiteral())

    private fun lengthAboveUpperBound(sampleData: StringValue) =
        sampleData.toStringLiteral().length > effectiveMaxLength

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
            5 > effectiveMaxLength -> effectiveMaxLength
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
            val regexWithoutCaretAndDollar = it.removePrefix("^").removeSuffix("$")
            StringValue(generateFromRegex(regexWithoutCaretAndDollar, patternBaseLength, effectiveMaxLength))
        } ?: StringValue(randomString(patternBaseLength))
    }


    private fun generateFromRegex(regexWithoutCaretAndDollar: String, minLength: Int, maxLength: Int? = null): String =
        if(this.maxLength != null) {
            Generex(regexWithoutCaretAndDollar).random(minLength, maxLength!!)
        } else {
            Generex(regexWithoutCaretAndDollar).random(minLength)
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
                    minLength = effectiveMaxLength.inc(),
                    maxLength = effectiveMaxLength.inc(),
                    regex = null
                )
                yield(
                    HasValue(pattern, "length greater than maxLength '$effectiveMaxLength'")
                )
            }
            if (minLength != null) {
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
            if (validRegex != null) {
                val pattern = copy(regex = validRegex.plus("_"))
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
