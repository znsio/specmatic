package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value
import java.util.*

object UUIDPattern : Pattern, ScalarPattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result = when (sampleData) {
        is StringValue -> resultOf {
            parse(sampleData.string, resolver)
            Result.Success()
        }

        else -> Result.Failure("UUID types can only be represented using strings")
    }

    override fun generate(resolver: Resolver): StringValue = StringValue(UUID.randomUUID().toString())

    override fun newBasedOn(row: Row, resolver: Resolver): List<UUIDPattern> = listOf(this)

    override fun newBasedOn(resolver: Resolver): List<UUIDPattern> = listOf(this)
    override fun negativeBasedOn(row: Row, resolver: Resolver): List<Pattern> {
        return listOf(NullPattern)
    }

    override fun parse(value: String, resolver: Resolver): StringValue =
        attempt {
            UUID.fromString(value)
            StringValue(value)
        }

    override fun encompasses(
        otherPattern: Pattern,
        thisResolver: Resolver,
        otherResolver: Resolver,
        typeStack: TypeStack,
    ): Result {
        return encompasses(this, otherPattern, thisResolver, otherResolver, typeStack)
    }

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value {
        return JSONArrayValue(valueList)
    }

    override val typeAlias: String?
        get() = null
    override val typeName: String = "uuid"
    override val pattern = "(uuid)"

    override fun toString() = pattern
}
