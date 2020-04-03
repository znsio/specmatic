package run.qontract.core.pattern

import run.qontract.core.ContractParseException
import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.value.NoValue
import run.qontract.core.value.Value

data class AnyPattern(override val pattern: List<Pattern>, override val key: String? = null) : Pattern, Keyed {
    override fun withKey(key: String?): Pattern = this.copy(key = key)

    override fun matches(sampleData: Value?, resolver: Resolver): Result =
        pattern.asSequence().map {
            resolver.matchesPattern(key, it, sampleData ?: NoValue)
        }.let { results ->
            results.find { it is Result.Success } ?: failedToFindAny(pattern, results.toList(), sampleData)
        }

    private fun failedToFindAny(pattern: List<Pattern>, results: List<Result>, sampleData: Value?): Result.Failure {
        val report = pattern.zip(results).map { (pattern, result) ->
            "Pattern: $pattern, result: $result"
        }.joinToString("\n")

        return Result.Failure("""Failed to match any of the available patterns.
            |$report
            |Value: $sampleData
        """.trimMargin())
    }

    override fun generate(resolver: Resolver): Value =
            when(key) {
                null -> pattern.random().generate(resolver)
                else -> resolver.generate(key, pattern.random())
            }

    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> =
            pattern.flatMap { it.newBasedOn(row, resolver) }

    override fun parse(value: String, resolver: Resolver): Value =
        pattern.asSequence().map {
            try { it.parse(value, resolver) } catch(e: Throwable) { null }
        }.find { it != null } ?: throw ContractParseException("Failed to parse value $value. It should have matched one of $pattern")
}
