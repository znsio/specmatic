package `in`.specmatic.core

import `in`.specmatic.conversions.EnvironmentAndPropertiesConfiguration
import `in`.specmatic.core.pattern.IgnoreUnexpectedKeys

const val POSITIVE_TEST_DESCRIPTION_PREFIX = "+ve "
const val NEGATIVE_TEST_DESCRIPTION_PREFIX = "-ve "

data class FlagsBased(
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

    fun withoutGenerativeTests(): FlagsBased {
        return this.copy(generation = NonGenerativeTests)
    }
}

fun strategiesFromFlags(flags: EnvironmentAndPropertiesConfiguration): FlagsBased {
    val (positivePrefix, negativePrefix) =
        if (flags.generativeTestingEnabled())
            Pair(POSITIVE_TEST_DESCRIPTION_PREFIX, NEGATIVE_TEST_DESCRIPTION_PREFIX)
        else
            Pair("", "")

    return FlagsBased(
        defaultExampleResolver = if (flags.schemaExampleDefaultEnabled()) UseDefaultExample else DoNotUseDefaultExample,
        generation = if (flags.generativeTestingEnabled()) GenerativeTestsEnabled() else NonGenerativeTests,
        unexpectedKeyCheck = if (flags.extensibleSchema()) IgnoreUnexpectedKeys else null,
        positivePrefix = positivePrefix,
        negativePrefix = negativePrefix
    )
}

val DefaultStrategies = FlagsBased (
    DoNotUseDefaultExample,
    NonGenerativeTests,
    null,
    "",
    ""
)
