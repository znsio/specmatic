package io.specmatic.core.pattern

import com.mifmif.common.regex.Generex
import dk.brics.automaton.RegExp
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
        regex?.let {
            val regexWithoutCaretAndDollar = it.removePrefix("^").removeSuffix("$")
            regexMinLengthValidation(regexWithoutCaretAndDollar)
            regexMaxLengthValidation(regexWithoutCaretAndDollar)
        }

    }

    private fun regexMinLengthValidation(it: String) {
        val automaton = RegExp(it).toAutomaton()

        minLength?.let {
            val shortestPossibleLengthOfRegex = automaton.getShortestExample(true).length
            if (shortestPossibleLengthOfRegex < it) {
                throw IllegalArgumentException("Invalid String Constraints - minLength cannot be greater than length of shortest possible string that matches regex")
            } else if (maxLength != null && shortestPossibleLengthOfRegex > maxLength) {
                throw IllegalArgumentException("Invalid String Constraints - maxLength cannot be less than length of shortest possible string that matches regex")
            }
        }
    }

    private fun regexMaxLengthValidation(regexWithoutCaretAndDollar: String) {
        maxLength?.let {
            val generatedString = generateFromRegex(regexWithoutCaretAndDollar, it+1)

            if (generatedString.length > it) {
                throw IllegalArgumentException("Invalid String Constraints - regex cannot generate / match string greater than maxLength")
            }
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

                if (regex != null && !Regex(regex).matches(sampleData.toStringLiteral())) {
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

    private val patternBaseLength: Int =
        when {
            minLength != null && 5 < minLength -> minLength
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

        return regex?.let {
            val regexWithoutCaretAndDollar = regex.removePrefix("^").removeSuffix("$")
            StringValue(generateFromRegex(regexWithoutCaretAndDollar, patternBaseLength, maxLength))
        } ?: StringValue(randomString(patternBaseLength))
    }


    private fun generateFromRegex(regexWithoutCaretAndDollar: String, minLength: Int, maxLength: Int? = null): String =
        maxLength?.let {
            Generex(regexWithoutCaretAndDollar).random(minLength, it)
        } ?: Generex(regexWithoutCaretAndDollar).random(minLength)


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
