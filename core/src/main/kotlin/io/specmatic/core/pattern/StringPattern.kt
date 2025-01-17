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
import org.cornutum.regexpgen.js.Provider
import org.cornutum.regexpgen.random.RandomBoundsGen
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
            val regexWithoutCaretAndDollar = validateRegex(it).removePrefix("^").removeSuffix("$")
            regexMinLengthValidation(regexWithoutCaretAndDollar)
            regexMaxLengthValidation(regexWithoutCaretAndDollar)
            Generex(regexWithoutCaretAndDollar)
        }

    }

    private fun validateRegex(regex: String): String {
        return runCatching { RegExp(regex); regex }.getOrElse {
            e -> throw IllegalArgumentException("Failed to parse regex ${regex.quote()}\nReason: ${e.message}")
        }
    }

    private fun regexMinLengthValidation(it: String) {
        val shortestPossibleLengthOfRegex = Provider.forEcmaScript().matchingExact(it).minLength

        minLength?.let {
            if (shortestPossibleLengthOfRegex < it) {
                throw IllegalArgumentException("Invalid String Constraints - minLength cannot be greater than length of shortest possible string that matches regex")
            } else if (maxLength != null && shortestPossibleLengthOfRegex > maxLength) {
                throw IllegalArgumentException("Invalid String Constraints - maxLength cannot be less than length of shortest possible string that matches regex")
            }
        }
    }

    private fun regexMaxLengthValidation(regexWithoutCaretAndDollar: String) {
            maxLength?.let {
                val finite = RegExp(regexWithoutCaretAndDollar).toAutomaton().isFinite
                if (!finite) {
                throw IllegalArgumentException("Invalid String Constraints - regex cannot generate infinite string when maxLength has been set")
                }
                val maxRegexLength = Provider.forEcmaScript().matchingExact(regexWithoutCaretAndDollar).maxLength
//                val regExp = RegExp(regex)
//                val automaton = regExp.toAutomaton()
//                val maxRegexLength2 = if (automaton.isFinite) {
//                    automaton.finiteStrings.map { it.length }.maxOrNull() ?: -1
//                } else {
//                    -1
//                }
                if (maxLength > maxRegexLength) {
                    throw IllegalArgumentException("Invalid String Constraints - regex cannot generate / match string greater than maxLength")
                }

            }
    }

    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if (sampleData?.hasTemplate() == true)
            return Result.Success()

        if (sampleData !is StringValue)
            return mismatchResult("string", sampleData, resolver.mismatchMessages)

        if (lengthBelowLowerBound(sampleData)) return mismatchResult(
            "string with minLength $minLength",
            sampleData, resolver.mismatchMessages
        )

        if (lengthAboveUpperBound(sampleData)) return mismatchResult(
            "string with maxLength $maxLength",
            sampleData, resolver.mismatchMessages
        )

        if (doesNotMatchRegex(sampleData)) {
            return mismatchResult(
                """string that matches regex /$regex/""",
                sampleData,
                resolver.mismatchMessages
            )
        }

        return Result.Success()
    }

    private fun doesNotMatchRegex(sampleData: StringValue) =
        regex != null && !Regex(regex).matches(sampleData.toStringLiteral())

    private fun lengthAboveUpperBound(sampleData: StringValue) =
        maxLength != null && sampleData.toStringLiteral().length > maxLength

    private fun lengthBelowLowerBound(sampleData: StringValue) =
        minLength != null && sampleData.toStringLiteral().length < minLength

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
        if(maxLength != null) {
            Provider.forEcmaScript().matchingExact(regexWithoutCaretAndDollar).generate(RandomBoundsGen(),minLength,maxLength)
        } else {
            Provider.forEcmaScript().matchingExact(regexWithoutCaretAndDollar).generate(RandomBoundsGen(),minLength,null)
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
