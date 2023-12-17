package `in`.specmatic.core

data class ResolverStrategies(val defaultExampleResolver: DefaultExampleResolver) {
    fun update(resolver: Resolver): Resolver {
        return resolver.copy(defaultExampleResolver = defaultExampleResolver)
    }
}

val StrategiesFromFlags = ResolverStrategies(
    if(Flags.schemaExampleDefaultEnabled()) UseDefaultExample() else DoNotUseDefaultExample()
)
