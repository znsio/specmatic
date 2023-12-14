package `in`.specmatic.core.pattern

import `in`.specmatic.core.value.NullValue
import `in`.specmatic.core.value.Value

private fun validEnumValues(values: List<Value>, key: String?, typeAlias: String?, example: String?): AnyPattern {
    assertThatAllValuesHaveTheSameType(values)
    return AnyPattern(values.map { ExactValuePattern(it) }, key, typeAlias, example)
}

fun assertThatAllValuesHaveTheSameType(values: List<Value>) {
    val types = values.filterNot { it is NullValue }.map { it.javaClass }
    val distinctTypes = types.distinct()

    if(distinctTypes.size > 1)
        throw ContractException("Enum values must all be of the same type. Found types: ${distinctTypes.joinToString(", ")}")
}

data class EnumPattern(
    override val pattern: AnyPattern
) : Pattern by pattern {
    constructor(values: List<Value>,
                key: String? = null,
                typeAlias: String? = null,
                example: String? = null
    ) : this(validEnumValues(values, key, typeAlias, example))

    fun withExample(example: String?): EnumPattern {
        return EnumPattern(pattern.copy(example = example))
    }

    override fun equals(other: Any?): Boolean = other is EnumPattern && other.pattern == this.pattern

    override fun hashCode(): Int = pattern.hashCode()
}