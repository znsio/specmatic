package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.value.EmptyString
import run.qontract.core.value.Value

data class AnyPattern(override val pattern: List<Pattern>, override val key: String? = null) : Pattern, Keyed {
    override fun equals(other: Any?): Boolean = other is AnyPattern && other.pattern == this.pattern

    override fun hashCode(): Int = pattern.hashCode()

    override fun withKey(key: String?): Pattern = this.copy(key = key)

    override fun matches(sampleData: Value?, resolver: Resolver): Result =
        pattern.asSequence().map {
            resolver.matchesPattern(key, it, sampleData ?: EmptyString)
        }.let { results ->
            results.find { it is Result.Success } ?: failedToFindAny(typeName, results.map { it as Result.Failure }.toList(), sampleData)
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
        }.find { it != null } ?: throw ContractException("Failed to parse value \"$value\". It should have matched one of ${pattern.joinToString(", ") { it.typeName }}.")

    override fun patternSet(resolver: Resolver): List<Pattern> =
            this.pattern.flatMap { it.patternSet(resolver) }

    override fun encompasses(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver): Result {
        return otherPattern.fitsWithin(patternSet(thisResolver), otherResolver, thisResolver)
    }

    override val typeName: String = pattern.joinToString(" or ") { inner -> inner.typeName }
}

private fun failedToFindAny(description: String, results: List<Result.Failure>, sampleData: Value?): Result.Failure {
    val report = results.joinToString("\n") { it.message }

    return Result.Failure("""${sampleData?.displayableValue()} failed to match $description
$report""".trim())
}
