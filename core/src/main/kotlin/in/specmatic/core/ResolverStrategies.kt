package `in`.specmatic.core

import `in`.specmatic.conversions.EnvironmentAndPropertiesConfiguration
import `in`.specmatic.core.pattern.IgnoreUnexpectedKeys

data class ResolverStrategies(
    val defaultExampleResolver: DefaultExampleResolver,
    val generation: GenerationStrategies,
    val unexpectedKeyCheck: UnexpectedKeyCheck?
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

fun strategiesFromFlags(flags: EnvironmentAndPropertiesConfiguration) = ResolverStrategies(
    defaultExampleResolver = if(flags.schemaExampleDefaultEnabled()) UseDefaultExample else DoNotUseDefaultExample,
    generation = if(flags.generativeTestingEnabled()) GenerativeTestsEnabled() else NonGenerativeTests,
    unexpectedKeyCheck = if(flags.extensibleSchema()) IgnoreUnexpectedKeys else null
)

val DefaultStrategies = ResolverStrategies (
    DoNotUseDefaultExample,
    NonGenerativeTests,
    null
)
