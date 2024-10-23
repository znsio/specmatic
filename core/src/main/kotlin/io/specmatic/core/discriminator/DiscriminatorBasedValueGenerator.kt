package io.specmatic.core.discriminator

import io.specmatic.core.Resolver
import io.specmatic.core.pattern.AnyPattern
import io.specmatic.core.pattern.ListPattern
import io.specmatic.core.pattern.Pattern
import io.specmatic.core.pattern.resolvedHop
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.Value

object DiscriminatorBasedValueGenerator {

    fun generateDiscriminatorBasedValues(resolver: Resolver, pattern: Pattern): List<DiscriminatorBasedItem<Value>> {
        return resolver.withCyclePrevention(pattern) { updatedResolver ->
            val resolvedPattern = resolvedHop(pattern, updatedResolver)

            if (resolvedPattern is ListPattern) {
                val listValuePattern = resolvedHop(resolvedPattern.pattern, updatedResolver)
                if (listValuePattern is AnyPattern && listValuePattern.isDiscriminatorPresent()) {
                    val values = listValuePattern.generateForEveryDiscriminatorValue(updatedResolver)
                    return@withCyclePrevention values.map {
                        it.copy(value = JSONArrayValue(listOf(it.value)))
                    }
                }
            }

            if (resolvedPattern !is AnyPattern || resolvedPattern.isDiscriminatorPresent().not()) {
                return@withCyclePrevention listOf(
                    DiscriminatorBasedItem(
                        discriminator = DiscriminatorMetadata(
                            discriminatorProperty = "",
                            discriminatorValue = "",
                        ),
                        value = resolvedPattern.generate(updatedResolver)
                    )
                )
            }
            resolvedPattern.generateForEveryDiscriminatorValue(updatedResolver)
        }
    }
}