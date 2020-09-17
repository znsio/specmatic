package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.mismatchResult
import run.qontract.core.value.JSONArrayValue
import run.qontract.core.value.NullValue
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value

const val NULL_TYPE = "(null))"

object NullPattern : Pattern, ScalarType {
    override fun matches(sampleData: Value?, resolver: Resolver): Result =
            when {
                sampleData is NullValue -> Result.Success()
                sampleData is StringValue && sampleData.string.isEmpty() -> Result.Success()
                else -> mismatchResult("null", sampleData)
            }

    override fun generate(resolver: Resolver): Value = NullValue
    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> = listOf(this)

    override fun parse(value: String, resolver: Resolver): Value =
        when(value.trim()) {
            NULL_TYPE -> NullValue
            "" -> NullValue
            else -> throw ContractException("Failed to parse $value: it is not null.")
        }

    override fun encompasses(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver, typeStack: TypeStack): Result {
        return encompasses(this, otherPattern, thisResolver, otherResolver, typeStack)
    }

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value {
        return JSONArrayValue(valueList)
    }

    override val typeAlias: String?
        get() = null

    override val typeName: String = "null"

    override val pattern: Any = NULL_TYPE
    override fun toString(): String = NULL_TYPE
}

internal fun isOptionalValuePattern(patternSpec: String): Boolean = withoutPatternDelimiters(patternSpec.trim()).endsWith("?")
internal fun withoutNullToken(patternSpec: String): String {
    return "(" + withoutPatternDelimiters(patternSpec.trim()).trim().removeSuffix("?") + ")"
}