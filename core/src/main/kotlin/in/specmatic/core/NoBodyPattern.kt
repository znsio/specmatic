package `in`.specmatic.core

import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value
import `in`.specmatic.core.NoBodyValue
import `in`.specmatic.core.pattern.*

object NoBodyPattern : Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if(sampleData == null)
            return Result.Success()

        if(sampleData is NoBodyValue)
            return Result.Success()

        return Result.Failure("Expected no body, but found ${sampleData.displayableType()}")
    }

    override fun generate(resolver: Resolver): Value = NoBodyValue

    override fun newBasedOn(row: Row, resolver: Resolver): Sequence<Pattern> = sequenceOf(this)
    override fun newBasedOnR(row: Row, resolver: Resolver): Sequence<ReturnValue<Pattern>> = sequenceOf(HasValue(this))

    override fun newBasedOn(resolver: Resolver): Sequence<Pattern> = sequenceOf(this)

    override fun negativeBasedOn(row: Row, resolver: Resolver): Sequence<ReturnValue<Pattern>> = emptySequence()

    override fun parse(value: String, resolver: Resolver): Value {
        return if(value.isBlank())
            NoBodyValue
        else
            StringValue(value)
    }

    override fun encompasses(
        otherPattern: Pattern,
        thisResolver: Resolver,
        otherResolver: Resolver,
        typeStack: TypeStack
    ): Result {
        if(otherPattern is NoBodyPattern)
            return Result.Success()

        return Result.Failure("Expected no body, but found ${otherPattern.typeName}")
    }

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value = JSONArrayValue(valueList)

    override val typeAlias: String?
        get() = null
    override val typeName: String
        get() = "no-body"
    override val pattern: Any
        get() = "no-body"

}
