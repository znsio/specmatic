package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.value.Value

interface Pattern {
    fun matches(sampleData: Value?, resolver: Resolver): Result
    fun generate(resolver: Resolver): Value
    fun newBasedOn(row: Row, resolver: Resolver): List<Pattern>

    fun parse(value: String, resolver: Resolver): Value
    fun matchesPattern(pattern: Pattern, resolver: Resolver): Boolean

    val description: String

    val pattern: Any
}
