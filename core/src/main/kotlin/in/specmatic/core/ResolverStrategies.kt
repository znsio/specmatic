package `in`.specmatic.core

data class ResolverStrategies(val defaultExampleResolver: DefaultExampleResolver)

val StrategiesFromFlags = ResolverStrategies(
    if(Flags.schemaExampleDefaultEnabled()) UseDefaultExample() else DoNotUseDefaultExample()
)
