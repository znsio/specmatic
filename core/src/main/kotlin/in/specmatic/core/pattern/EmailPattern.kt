package `in`.specmatic.core.pattern

import `in`.specmatic.core.*
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value
import java.util.*

private const val EMAIL_REGEX = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}\$"

class EmailPattern (private val stringPatternDelegate: StringPattern) :
    Pattern by stringPatternDelegate {

    constructor(
        typeAlias: String? = null,
        minLength: Int? = null,
        maxLength: Int? = null,
        example: String? = null
    ) : this(StringPattern(typeAlias, minLength, maxLength, example))

    companion object {
        val emailRegex = Regex(EMAIL_REGEX)
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

    override fun negativeBasedOn(row: Row, resolver: Resolver): List<Pattern> {
        return stringPatternDelegate.negativeBasedOn(row, resolver).plus(StringPattern())
    }

    override fun encompasses(
        otherPattern: Pattern,
        thisResolver: Resolver,
        otherResolver: Resolver,
        typeStack: TypeStack
    ): Result {
        val resolvedOther = resolvedHop(otherPattern, otherResolver)
        if(resolvedOther !is EmailPattern) return Result.Failure("Expected email, get ${resolvedOther.typeAlias}")

        return stringPatternDelegate.encompasses(resolvedOther.stringPatternDelegate, thisResolver, otherResolver, typeStack)
    }

    override val typeName: String
        get() = "email"
}
