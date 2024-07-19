package io.specmatic.core.pattern

import io.specmatic.core.Resolver
import io.specmatic.core.Result
import io.specmatic.core.mismatchResult
import io.specmatic.core.pattern.config.NegativePatternConfiguration
import io.specmatic.core.value.StringValue
import io.specmatic.core.value.Value
import java.util.*

private const val EMAIL_REGEX = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}\$"

class EmailPattern (private val stringPatternDelegate: StringPattern) :
    Pattern by stringPatternDelegate, ScalarType {

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

    override fun newBasedOn(row: Row, resolver: Resolver): Sequence<ReturnValue<Pattern>> {
        return sequenceOf(HasValue(this))
    }

    override fun negativeBasedOn(row: Row, resolver: Resolver, config: NegativePatternConfiguration): Sequence<ReturnValue<Pattern>> {
        val negativePatterns = stringPatternDelegate.negativeBasedOn(row, resolver).map { it.value }.plus(StringPattern())
        return scalarAnnotation(this, negativePatterns)
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
