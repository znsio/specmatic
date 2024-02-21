package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value

//TODO: Does this include Null?
object AnythingPattern: Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        return Result.Success()
    }

    override fun generate(resolver: Resolver): Value {
        return StringValue(randomString(10))
    }

    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> {
        return listOf(this)
    }

    override fun newBasedOn(resolver: Resolver): List<Pattern> {
        return listOf(this)
    }

    override fun negativeBasedOn(row: Row, resolver: Resolver): List<Pattern> {
        return listOf(this)
    }

    override fun parse(value: String, resolver: Resolver): Value {
        return StringValue(value)
    }

    override fun encompasses(
        otherPattern: Pattern,
        thisResolver: Resolver,
        otherResolver: Resolver,
        typeStack: TypeStack
    ): Result {
        return when(otherPattern) {
            AnythingPattern -> Result.Success()
            else -> Result.Failure("Changing from anyType to ${otherPattern.typeName} is a breaking change.")
        }
    }

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value {
        return StringValue()
    }

    override fun testCount(resolver: Resolver): ULong {
        return 1.toULong()
    }

    override val typeAlias: String? = null

    override val typeName: String = "anything"

    override val pattern: Any = "(anything)"
}