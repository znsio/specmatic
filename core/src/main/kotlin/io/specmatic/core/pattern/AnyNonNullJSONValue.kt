package io.specmatic.core.pattern

import io.specmatic.core.Resolver
import io.specmatic.core.Result
import io.specmatic.core.value.NullValue
import io.specmatic.core.value.Value

data class AnyNonNullJSONValue(override val pattern: Pattern = AnythingPattern): Pattern by pattern{
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if(sampleData is NullValue)
            return resolver.mismatchMessages.valueMismatchFailure("non-null value", sampleData)

        return Result.Success()
    }

    override fun encompasses(
        otherPattern: Pattern,
        thisResolver: Resolver,
        otherResolver: Resolver,
        typeStack: TypeStack
    ): Result {
        return when(otherPattern) {
            AnyNonNullJSONValue() -> Result.Success()
            else -> Result.Failure("Changing from anyType to ${otherPattern.typeName} is a breaking change.")
        }
    }
}
