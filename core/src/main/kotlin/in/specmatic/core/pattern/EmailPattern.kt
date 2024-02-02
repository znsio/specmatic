package `in`.specmatic.core.pattern

import `in`.specmatic.core.*
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value
import java.util.*

private const val EMAIL_REGEX = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}\$"

class EmailPattern private constructor(private val stringPatternDelegate: StringPattern) :
    Pattern by stringPatternDelegate {

    companion object {
        val emailRegex = Regex(EMAIL_REGEX)

        fun create(
            typeAlias: String? = null,
            minLength: Int? = null,
            maxLength: Int? = null,
            example: String? = null
        ): EmailPattern {
            val stringPattern = StringPattern(typeAlias, minLength, maxLength, example)
            return EmailPattern(stringPattern)
        }
    }

    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if (sampleData !is StringValue) return mismatchResult("email string", sampleData, resolver.mismatchMessages)
        val email = sampleData.toStringLiteral()
        return if (emailRegex.matches(email)) {
            Result.Success()
        } else {
            mismatchResult("email string", sampleData, resolver.mismatchMessages)
        }
    }

    override fun generate(resolver: Resolver): Value {
        val localPart = randomString(5).lowercase(Locale.getDefault())
        val domain = randomString(5).lowercase(Locale.getDefault())
        return StringValue("$localPart@$domain.com")
    }

    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> = listOf(this)
}
