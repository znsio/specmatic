package io.specmatic.core

import io.specmatic.core.pattern.*
import io.specmatic.core.pattern.config.NegativePatternConfiguration
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.StringValue
import io.specmatic.core.value.Value

object NoBodyPattern : Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if(sampleData is NoBodyValue || sampleData == null)
            return Result.Success()

        return Result.Failure("Expected no body, but found ${sampleData.displayableType()}")
    }

    override fun generate(resolver: Resolver): Value = NoBodyValue

    override fun newBasedOn(row: Row, resolver: Resolver): Sequence<ReturnValue<Pattern>> = sequenceOf(HasValue(this))

    override fun newBasedOn(resolver: Resolver): Sequence<Pattern> = sequenceOf(this)

    override fun negativeBasedOn(row: Row, resolver: Resolver, config: NegativePatternConfiguration): Sequence<ReturnValue<Pattern>> = emptySequence()

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

//        TODO -
//          response changed from no-body to body -> backward compatible
//          response changed from body to no-body -> backward incompatible
/*
            When response changes from body to no-body, this function won't get called.
            The encompasses from SomeBodyPattern associated with the body will get called.
            That function will return Failure (assumption)

            When response changes from no-body to body, this function gets called:
            e.g. NobodyPattern.encompasses(SomeBodyPattern)
            In such case we want to return Success, that's why the logic is as follows.
*/

        return Result.Success()
//        if(otherPattern is NoBodyPattern)
//            return Result.Success()
//
//        return Result.Failure("Expected no body, but found ${otherPattern.typeName}")
    }

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value = JSONArrayValue(valueList)

    override val typeAlias: String?
        get() = null
    override val typeName: String
        get() = "no-body"
    override val pattern: Any
        get() = "no-body"

}
