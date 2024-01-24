package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.mismatchResult
import `in`.specmatic.core.value.BooleanValue
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.Value
import java.util.*

data class BooleanPattern(override val example: String? = null) : Pattern, ScalarType, HasDefaultExample {
    override fun matches(sampleData: Value?, resolver: Resolver): Result =
        when(sampleData) {
            is BooleanValue -> Result.Success()
            else -> mismatchResult("boolean", sampleData, resolver.mismatchMessages)
        }

    override fun generate(resolver: Resolver): Value =
        resolver.resolveExample(example, this) ?: randomBoolean()

    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> = listOf(this)
    override fun newBasedOn(resolver: Resolver): List<Pattern> = listOf(this)
    override fun negativeBasedOn(row: Row, resolver: Resolver): List<Pattern> {
        return listOf(NullPattern)
    }

    override fun parse(value: String, resolver: Resolver): Value = when (value.lowercase()) {
        !in listOf("true", "false") -> throw ContractException(mismatchResult(BooleanPattern(), value, resolver.mismatchMessages).toFailureReport())
        else -> BooleanValue(value.toBoolean())
    }
    override fun encompasses(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver, typeStack: TypeStack): Result {
        return encompasses(this, otherPattern, thisResolver, otherResolver, typeStack)
    }

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value {
        return JSONArrayValue(valueList)
    }

    override val typeAlias: String?
        get() = null

    override val typeName: String = "boolean"
    override val pattern: Any = "(boolean)"
    override fun toString(): String = pattern.toString()
}

fun randomBoolean() = when (Random().nextInt(2)) {
    0 -> BooleanValue(false)
    else -> BooleanValue(true)
}
