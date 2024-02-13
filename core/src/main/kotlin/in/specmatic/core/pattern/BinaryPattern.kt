package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.mismatchResult
import `in`.specmatic.core.value.BinaryValue
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value
import java.security.SecureRandom

data class BinaryPattern(
    override val typeAlias: String? = null,
) : Pattern, ScalarPattern {

    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        return when (sampleData) {
            is StringValue -> return Result.Success()
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

    override fun generate(resolver: Resolver): Value {
        val secureRandom = SecureRandom()
        val bytes = ByteArray(20)
        secureRandom.nextBytes(bytes)
        return BinaryValue(bytes)
    }

    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> = listOf(this)
    override fun newBasedOn(resolver: Resolver): List<Pattern> = listOf(this)
    override fun negativeBasedOn(row: Row, resolver: Resolver): List<Pattern> {
        return listOf(NullPattern, NumberPattern(), BooleanPattern())
    }

    override fun parse(value: String, resolver: Resolver): Value = StringValue(value)
    override val typeName: String = "string"

    override val pattern: Any = "(string)"
    override fun toString(): String = pattern.toString()
}