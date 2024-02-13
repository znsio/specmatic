package `in`.specmatic.core

import `in`.specmatic.core.pattern.Pattern
import `in`.specmatic.core.pattern.Row
import `in`.specmatic.core.pattern.TypeStack
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value
import `in`.specmatic.core.NoBodyValue

object NoBodyPattern : Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if(sampleData == null)
            return Result.Success()

//        if(sampleData is StringValue && sampleData.string.isEmpty())
//            return Result.Success()

        if(sampleData is NoBodyValue)
            return Result.Success()

        return Result.Failure("Expected no body, but found ${sampleData.displayableType()}")
    }

    override fun generate(resolver: Resolver): Value = NoBodyValue

    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> = listOf(this)

    override fun newBasedOn(resolver: Resolver): List<Pattern> = listOf(this)

    override fun negativeBasedOn(row: Row, resolver: Resolver): List<Pattern> = emptyList()

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
    override fun complexity(resolver: Resolver): ULong {
        return 1.toULong()
    }

    override val typeAlias: String?
        get() = null
    override val typeName: String
        get() = "no-body"
    override val pattern: Any
        get() = "no-body"

}
