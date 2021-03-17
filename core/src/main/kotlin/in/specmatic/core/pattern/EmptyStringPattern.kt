package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.mismatchResult
import `in`.specmatic.core.value.*

object EmptyStringPattern : Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        return when (sampleData) {
            EmptyString -> Result.Success()
            else -> mismatchResult("empty string", sampleData)
        }
    }

    override fun generate(resolver: Resolver): Value = StringValue("")
    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> = listOf(this)
    override fun parse(value: String, resolver: Resolver): Value {
        return when {
            value.isEmpty() -> EmptyString
            else -> throw ContractException("""No data was expected, but got "$value" instead""")
        }
    }

    override fun encompasses(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver, typeStack: TypeStack): Result {
        if(otherPattern is EmptyStringPattern) return Result.Success()
        return Result.Failure("No data was expected, but got \"${otherPattern.typeName}\" instead")
    }

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value {
        return JSONArrayValue(valueList)
    }

    override val typeAlias: String?
        get() = null

    override val typeName: String = "nothing"

    override val pattern: Any = ""

    override fun toString(): String = "(nothing)"
}