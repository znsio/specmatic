package `in`.specmatic.core

import `in`.specmatic.conversions.EnvironmentAndPropertiesConfiguration
import `in`.specmatic.core.pattern.IgnoreUnexpectedKeys

data class ResolverStrategies(
    val defaultExampleResolver: DefaultExampleResolver,
    val generation: GenerationStrategies,
    val unexpectedKeyCheck: UnexpectedKeyCheck?
) {
    fun update(resolver: Resolver): Resolver {
        return resolver.copy(
            defaultExampleResolver = defaultExampleResolver,
            generation = generation
        ).let {
            if(unexpectedKeyCheck != null) {
                val keyCheck = resolver.findKeyErrorCheck
                val updatedKeyCheck = keyCheck.copy(unexpectedKeyCheck = unexpectedKeyCheck)
                resolver.copy(findKeyErrorCheck = updatedKeyCheck)
            } else
                it
        }
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
