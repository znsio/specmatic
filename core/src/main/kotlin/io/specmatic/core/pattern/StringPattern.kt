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

const val WORD_BOUNDARY = "\\b"

data class StringPattern (
    override val typeAlias: String? = null,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    override val example: String? = null,
    val regex: String? = null
) : Pattern, ScalarType, HasDefaultExample {
    val validRegex
        get() = regex?.let {
            validateRegex(replaceRegexLowerBounds(it))
                .removePrefix("^").removeSuffix("$")
                .removePrefix(WORD_BOUNDARY).removeSuffix(WORD_BOUNDARY)
                .let { regexWithoutSurroundingDelimiters -> replaceShorthandEscapeSeq(regexWithoutSurroundingDelimiters) }
        }
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

    private fun replaceShorthandEscapeSeq(regex: String): String {
        // This regex matches a character class: a '[' followed by either an escaped char (e.g. \])
        // or any char that is not a ']', until the first unescaped ']'
        val charClassRegex = Regex("""\[(?:\\.|[^\]])*]""")

        return charClassRegex.replace(regex) { matchResult ->
            val charClass = matchResult.value

            // Check if the class is negated (i.e. starts with "[^")
            val isNegated = charClass.startsWith("[^")
            // Extract the inner content (skip the starting '[' or "[^" and the ending ']')
            val innerContent = if (isNegated)
                charClass.substring(2, charClass.length - 1)
            else
                charClass.substring(1, charClass.length - 1)

            // Replace shorthand escapes inside the character class.
            // Note: The replacements are applied only to the inner content.
            val replaced = innerContent
                .replace(Regex("""\\w"""), "a-zA-Z0-9_")
                .replace(Regex("""\\s"""), " \\t\\r\\n\\f")
                .replace(Regex("""\\d"""), "0-9")

            // Rebuild the character class with its original negation if present.
            if (isNegated) "[^$replaced]" else "[$replaced]"
        }
    }

    private fun regexMinLengthValidation(regex: String) {
        minLength?.let {
            val regExSpec = RegExSpec(regex)
            val shortestString = regExSpec.generateShortestString()
            if (it > shortestString.length && regExSpec.isFinite) {
                val longestString = regExSpec.generateLongestStringOrRandom(it)
                if (longestString.length < it) {
                    throw IllegalArgumentException("Invalid String Constraints - minLength $it cannot be greater than the length of longest possible string that matches regex ${this.regex}")
                }
            }
        }
    }

    private fun regexMaxLengthValidation(regex: String) {
        maxLength?.let {
            val shortestPossibleString = RegExSpec(regex).generateShortestString()
            if (shortestPossibleString.length > it) {
                throw IllegalArgumentException("Invalid String Constraints - maxLength $it cannot be less than the length of shortest possible string that matches regex ${this.regex}")
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
                """string that matches regex $validRegex""",
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
        val regExSpec = RegExSpec(validRegex)
        val minLengthExample: ReturnValue<Pattern>? = minLength?.let { minLen ->
            val exampleString = regExSpec.generateShortestStringOrRandom(minLen)
            HasValue(ExactValuePattern(StringValue(exampleString)), "minimum length string")
        }

        val withinRangeExample: ReturnValue<Pattern> = HasValue(this)

        val maxLengthExample: ReturnValue<Pattern>? = maxLength?.let { maxLen ->
            val exampleString = regExSpec.generateLongestStringOrRandom(maxLen)
            HasValue(ExactValuePattern(StringValue(exampleString)), "maximum length string")
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
