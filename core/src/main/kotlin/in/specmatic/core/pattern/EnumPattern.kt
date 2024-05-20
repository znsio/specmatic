package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.value.NullValue
import `in`.specmatic.core.value.Value

private fun validEnumValues(values: List<Value>, key: String?, typeAlias: String?, example: String?, nullable: Boolean): AnyPattern {
    assertThatAllValuesHaveTheSameType(values, nullable)
    return AnyPattern(values.map { ExactValuePattern(it) }, key, typeAlias, example)
}

fun not(boolean: Boolean) = !boolean
fun assertThatAllValuesHaveTheSameType(values: List<Value>, enumIsNullable: Boolean) {
    val enumOptionsContainNull = values.any { it is NullValue }

    if(not(enumIsNullable) yet enumOptionsContainNull)
        throw ContractException("Enum values cannot be null as the enum is not nullable")

    val types = values.filterNot { it is NullValue }.map { it.javaClass }
    val distinctTypes = types.distinct()

    if(distinctTypes.size > 1)
        throw ContractException("Enum values must all be of the same type. Found types: ${distinctTypes.joinToString(", ")}")
}

private infix fun Boolean.yet(otherBooleanValue: Boolean): Boolean {
    return this && otherBooleanValue
}

data class EnumPattern(
    override val pattern: AnyPattern,
    val nullable: Boolean
) : Pattern by pattern, ScalarType {
    constructor(values: List<Value>,
                key: String? = null,
                typeAlias: String? = null,
                example: String? = null,
                nullable: Boolean = false
    ) : this(validEnumValues(values, key, typeAlias, example, nullable), nullable)

    fun withExample(example: String?): EnumPattern {
        return this.copy(pattern = pattern.copy(example = example))
    }

    override fun newBasedOnR(row: Row, resolver: Resolver): Sequence<ReturnValue<Pattern>> {
        return pattern.newBasedOnR(row, resolver).map {
            it.ifHasValue {
                HasValue(it.value, "")
            }
        }
    }

    override fun negativeBasedOn(row: Row, resolver: Resolver): Sequence<ReturnValue<Pattern>> {
        return scalarAnnotation(this, pattern.negativeBasedOn(row, resolver).map { it.value })
    }

    override fun equals(other: Any?): Boolean = other is EnumPattern && other.pattern == this.pattern

    override fun hashCode(): Int = pattern.hashCode()

    override fun toNullable(defaultValue: String?): Pattern {
        return this
    }
}