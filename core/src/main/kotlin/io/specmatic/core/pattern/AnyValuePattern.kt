package io.specmatic.core.pattern

import io.specmatic.core.Resolver
import io.specmatic.core.Result
import io.specmatic.core.pattern.config.NegativePatternConfiguration
import io.specmatic.core.value.Value

object AnyValuePattern : Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        return Result.Success()
    }

    override fun generate(resolver: Resolver): Value {
        throw ContractException("This pattern should not be used except in a test to generate any value")
    }

    override fun newBasedOn(row: Row, resolver: Resolver): Sequence<ReturnValue<Pattern>> {
        throw ContractException("This pattern should not be used except in a test to test any value")
    }

    override fun newBasedOn(resolver: Resolver): Sequence<Pattern> {
        throw ContractException("This pattern should not be used except in a test to test any value")
    }

    override fun negativeBasedOn(
        row: Row,
        resolver: Resolver,
        config: NegativePatternConfiguration
    ): Sequence<ReturnValue<Pattern>> {
        throw ContractException("This pattern should not be used except in a test to negative test any value")
    }

    override fun parse(value: String, resolver: Resolver): Value {
        throw ContractException("This pattern should not be used except in a test to parse any value")
    }

    override fun encompasses(
        otherPattern: Pattern,
        thisResolver: Resolver,
        otherResolver: Resolver,
        typeStack: TypeStack
    ): Result {
        throw ContractException("This pattern should not be used except in a test to encompass any value")
    }

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value {
        throw ContractException("This pattern should not be used except in a test to get a list for any value")
    }

    override val typeAlias: String?
        get() = null
    override val typeName: String
        get() = "(anyvalue)"
    override val pattern: Any
        get() = throw ContractException("This pattern should not be used except in a test to get a pattern for any value")
}
