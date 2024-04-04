package `in`.specmatic.core

import `in`.specmatic.conversions.EnvironmentAndPropertiesConfiguration
import `in`.specmatic.core.pattern.IgnoreUnexpectedKeys
import net.bytebuddy.implementation.ToStringMethod.PrefixResolver

data class ResolverStrategies(
    val defaultExampleResolver: DefaultExampleResolver,
    val generation: GenerationStrategies,
    val unexpectedKeyCheck: UnexpectedKeyCheck?,
    val positivePrefix: String,
    val negativePrefix: String
) {
    fun update(resolver: Resolver): Resolver {
        val findKeyErrorCheck = if(unexpectedKeyCheck != null) {
            resolver.findKeyErrorCheck.copy(unexpectedKeyCheck = unexpectedKeyCheck)
        } else
            resolver.findKeyErrorCheck

        return resolver.copy(
            defaultExampleResolver = defaultExampleResolver,
            generation = generation,
            findKeyErrorCheck = findKeyErrorCheck
        )
    }

    fun withoutGenerativeTests(): ResolverStrategies {
        return this.copy(generation = NonGenerativeTests)
    }
}

fun strategiesFromFlags(flags: EnvironmentAndPropertiesConfiguration): ResolverStrategies {
    val (positivePrefix, negativePrefix) =
        if (flags.generativeTestingEnabled())
            Pair(POSITIVE_TEST_DESCRIPTION_PREFIX, NEGATIVE_TEST_DESCRIPTION_PREFIX)
        else
            Pair("", "")

    return ResolverStrategies(
        defaultExampleResolver = if (flags.schemaExampleDefaultEnabled()) UseDefaultExample else DoNotUseDefaultExample,
        generation = if (flags.generativeTestingEnabled()) GenerativeTestsEnabled() else NonGenerativeTests,
        unexpectedKeyCheck = if (flags.extensibleSchema()) IgnoreUnexpectedKeys else null,
        positivePrefix = positivePrefix,
        negativePrefix = negativePrefix
    )
}

val DefaultStrategies = ResolverStrategies (
    DoNotUseDefaultExample,
    NonGenerativeTests,
    null,
    "",
    ""
)
