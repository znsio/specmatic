package `in`.specmatic.core

data class ResolverStrategies(
    val defaultExampleResolver: DefaultExampleResolver,
    val generation: GenerationStrategies,
) {
    fun update(resolver: Resolver): Resolver {
        return resolver.copy(
            defaultExampleResolver = defaultExampleResolver,
            generation = generation
        )
    }
}

fun strategiesFromFlags() = ResolverStrategies(
    defaultExampleResolver = if(Flags.schemaExampleDefaultEnabled()) UseDefaultExample else DoNotUseDefaultExample,
    generation = if(Flags.generativeTestingEnabled()) GenerativeTestsEnabled() else NonGenerativeTests
)

val DefaultStrategies = ResolverStrategies (
    DoNotUseDefaultExample,
    NonGenerativeTests
)
