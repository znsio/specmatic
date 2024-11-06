package io.specmatic.core.pattern

import io.specmatic.core.FailureReason
import io.specmatic.core.Resolver
import io.specmatic.core.Result
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.Value

class Discriminator(
    private val discriminatorProperty: String,
    private val discriminatorValues: Set<String>
) {
    companion object {
        fun create(discriminatorProperty: String? = null, discriminatorValues: Set<String> = emptySet()): Discriminator? {
            if(discriminatorProperty == null || discriminatorValues.isEmpty())
                return null

            return Discriminator(discriminatorProperty, discriminatorValues)
        }
    }

    fun matches(sampleData: Value?, pattern: List<Pattern>, key: String?, resolver: Resolver): Result {
        if (sampleData !is JSONObjectValue)
            return jsonObjectMismatchError(resolver, sampleData)

        val discriminatorCsvClause = if(discriminatorValues.size ==  1)
            discriminatorValues.first()
        else
            "one of ${discriminatorValues.joinToString(", ")}"

        val actualDiscriminatorValue = sampleData.jsonObject[discriminatorProperty] ?: return discriminatorKeyMissingFailure(
            discriminatorProperty,
            discriminatorCsvClause
        )

        if (actualDiscriminatorValue.toStringLiteral() !in discriminatorValues) {
            val message =
                "Expected the value of discriminator property to be $discriminatorCsvClause but it was ${actualDiscriminatorValue.toStringLiteral()}"

            return Result.Failure(
                message,
                breadCrumb = discriminatorProperty,
                failureReason = FailureReason.DiscriminatorMismatch
            )
        }

        val emptyPatternMatchResults = Pair(emptyList<AnyPattern.AnyPatternMatch>(), false)

        val (matchResults: List<AnyPattern.AnyPatternMatch>, discriminatorMatchOccurred: Boolean) = pattern.fold(emptyPatternMatchResults) { acc, pattern ->
            val (resultsSoFar, discriminatorMatchHasOccurred) = acc

            if (discriminatorMatchHasOccurred) {
                return@fold resultsSoFar.plus(discriminatorMatchFailure(pattern)) to true
            }

            val discriminatorProbe = JSONObjectValue(mapOf(discriminatorProperty to actualDiscriminatorValue))

            val discriminatorMatched = pattern.matches(discriminatorProbe, resolver).let { probeResult ->
                probeResult is Result.Success
                        || (probeResult is Result.Failure && probeResult.hasReason(FailureReason.FailedButDiscriminatorMatched))
            }

            if(discriminatorMatched)
                resultsSoFar.plus((AnyPattern.AnyPatternMatch(
                    pattern,
                    resolver.matchesPattern(key, pattern, sampleData)
                ))) to true
            else
                resultsSoFar.plus(discriminatorMatchFailure(pattern)) to false
        }

        if(!discriminatorMatchOccurred) {
            return Result.Failure(
                "Discriminator property $discriminatorProperty is missing from the spec",
                breadCrumb = discriminatorProperty,
                failureReason = FailureReason.DiscriminatorMismatch
            )
        }

        val matchResult = matchResults.find { it.result is Result.Success }

        if(matchResult != null)
            return matchResult.result

        val failures = matchResults.map { it.result }.filterIsInstance<Result.Failure>()

        val deepMatchResults = failures.filter { it.hasReason(FailureReason.FailedButDiscriminatorMatched) }

        return if(deepMatchResults.isNotEmpty())
            Result.Failure.fromFailures(deepMatchResults)
                .removeReasonsFromCauses().copy(failureReason = FailureReason.FailedButDiscriminatorMatched)
        else
            Result.Failure.fromFailures(failures).removeReasonsFromCauses()

    }

    private fun discriminatorMatchFailure(pattern: Pattern) = AnyPattern.AnyPatternMatch(
        pattern,
        Result.Failure(
            "Discriminator match failure",
            failureReason = FailureReason.DiscriminatorMismatch
        )
    )

    private fun jsonObjectMismatchError(
        resolver: Resolver,
        sampleData: Value?
    ) = resolver.mismatchMessages.valueMismatchFailure("json object", sampleData)

    private fun discriminatorKeyMissingFailure(discriminatorProperty: String, discriminatorCsv: String) =
        Result.Failure(
            "Discriminator property $discriminatorProperty is missing from the object (it's value should be $discriminatorCsv)",
            breadCrumb = discriminatorProperty,
            failureReason = FailureReason.DiscriminatorMismatch
        )
}