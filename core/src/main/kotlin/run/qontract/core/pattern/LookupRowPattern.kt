package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.resultReport
import run.qontract.core.value.EmptyString
import run.qontract.core.value.Value

data class LookupRowPattern(override val pattern: Pattern, val key: String) : Pattern {
    override fun equals(other: Any?): Boolean = other is LookupRowPattern && other.pattern == pattern
    override fun hashCode(): Int = pattern.hashCode()

    override fun matches(sampleData: Value?, resolver: Resolver): Result =
            resolver.matchesPattern(null, pattern, sampleData ?: EmptyString)

    override fun generate(resolver: Resolver): Value = pattern.generate(resolver)

    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> {
        return when {
            row.containsField(key) -> {
                val rowValue = row.getField(key)

                when {
                    isPatternToken(rowValue) -> {
                        val rowPattern = parsedPattern(rowValue)
                        when(val result = pattern.encompasses(rowPattern, resolver, resolver)) {
                            is Result.Success -> listOf(rowPattern)
                            else -> throw ContractException(resultReport(result))
                        }
                    }
                    else -> listOf(ExactValuePattern(pattern.parse(rowValue, resolver)))
                }
            }
            else -> {
                pattern.newBasedOn(row, resolver)
            }
        }
    }

    override fun parse(value: String, resolver: Resolver): Value = pattern.parse(value, resolver)

    override fun encompasses(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver): Result {
        return pattern.encompasses(resolvedHop(otherPattern, otherResolver), thisResolver, otherResolver)
    }

    override val typeName: String = pattern.typeName
}