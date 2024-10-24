package io.specmatic.core.discriminator

import io.specmatic.core.Resolver
import io.specmatic.core.pattern.AnyPattern
import io.specmatic.core.pattern.JSONObjectPattern
import io.specmatic.core.pattern.ListPattern
import io.specmatic.core.pattern.Pattern
import io.specmatic.core.pattern.resolvedHop
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.Value

object DiscriminatorBasedValueGenerator {

    fun generateDiscriminatorBasedValues(
        resolver: Resolver,
        pattern: Pattern,
        createEventBasedValues: Boolean = true
    ): List<DiscriminatorBasedItem<Value>> {
        val eventBasedDiscriminatorValues = if(createEventBasedValues) {
            generateEventDiscriminatorBasedValues(resolver, pattern)
        } else emptyList()

        return resolver.withCyclePrevention(pattern) { updatedResolver ->
            val resolvedPattern = resolvedHop(pattern, updatedResolver)

            if(isEventBasedPattern(resolvedPattern) && createEventBasedValues) {
                return@withCyclePrevention emptyList()
            }

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
        }.plus(eventBasedDiscriminatorValues)
    }

    private fun generateEventDiscriminatorBasedValues(
        resolver: Resolver,
        pattern: Pattern
    ): List<DiscriminatorBasedItem<Value>> {
        return resolver.withCyclePrevention(pattern) { updatedResolver ->
            val resolvedPattern = resolvedHop(pattern, updatedResolver)

            if(resolvedPattern !is AnyPattern) return@withCyclePrevention emptyList()
            if(resolvedPattern.pattern.firstOrNull() !is JSONObjectPattern) return@withCyclePrevention emptyList()

            val eventContainerPattern = resolvedPattern.pattern.first() as JSONObjectPattern
            val eventPattern = eventContainerPattern.getEvent() ?: return@withCyclePrevention emptyList()

            val resolvedEventPattern = resolvedHop(eventPattern, updatedResolver)
            if(resolvedEventPattern !is JSONObjectPattern) return@withCyclePrevention emptyList()

            // assuming there is a single key and that key has a discriminator value associated with it
            val patternWithDiscriminator = resolvedEventPattern.pattern.entries.first().value
            val patternWithDiscriminatorKey = resolvedEventPattern.pattern.entries.first().key

            val discriminatorBasedValues = generateDiscriminatorBasedValues(
                updatedResolver,
                patternWithDiscriminator,
                false
            )
            val eventValues = discriminatorBasedValues.map {
                it.copy(
                    value = JSONObjectValue(
                        mapOf(
                            patternWithDiscriminatorKey to it.value
                        )
                    )
                )
            }

            val eventContainerValue = generateDiscriminatorBasedValues(
                updatedResolver,
                eventContainerPattern,
                false
            ).first()

            val updatedEventContainerWithEventDiscriminatorValues =
                eventValues.map { discriminatorBasedValue ->
                    val eventContainerMap =
                        (eventContainerValue.value as JSONObjectValue).jsonObject
                    val eventKey = if (eventContainerMap.containsKey("event")) "event" else "event?"

                    val updatedEventContainerMap = eventContainerMap.mapValues {
                        if (it.key == eventKey) return@mapValues discriminatorBasedValue.value
                        it.value
                    }
                    discriminatorBasedValue.copy(
                        value = eventContainerValue.value.copy(
                            jsonObject = updatedEventContainerMap
                        )
                    )
                }

            updatedEventContainerWithEventDiscriminatorValues
        }
    }

    private fun isEventBasedPattern(
        resolvedPattern: Pattern
    ): Boolean {
        if(resolvedPattern !is AnyPattern) return false
        if(resolvedPattern.pattern.firstOrNull() !is JSONObjectPattern) return false

        val eventContainerPattern = resolvedPattern.pattern.first() as JSONObjectPattern
        val eventPattern = eventContainerPattern.getEvent() ?: return false
        return true
    }

    private fun JSONObjectPattern.getEvent(): Pattern? {
        return this.pattern["event"] ?: this.pattern["event?"]
    }
}