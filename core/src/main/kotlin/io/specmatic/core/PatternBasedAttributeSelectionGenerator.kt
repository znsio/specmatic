package io.specmatic.core

import io.specmatic.core.discriminator.DiscriminatorMetadata
import io.specmatic.core.pattern.AnyPattern
import io.specmatic.core.pattern.Pattern
import io.specmatic.core.pattern.resolvedHop
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.Value

object PatternBasedAttributeSelectionGenerator {
    fun getAttributeFromPattern(resolver: Resolver, attributeSelectionPattern: AttributeSelectionPattern, patternWithFields: Pattern): List<AttributeSelectionMetadata> {
        val basePattern = resolvedHop(patternWithFields, resolver)
        if (basePattern is AnyPattern) return getAttributesFromAnyPattern(resolver, attributeSelectionPattern, basePattern)

        val basePatternValue = basePattern.generate(resolver)
        val mandatoryKeys = patternWithFields.eliminateOptionalKey(basePatternValue, resolver).getTopLevelKeys()

        val baseMetadata = attributeSelectionPattern.toAttributeSelectionMetadata()
        return mandatoryKeys.filterNot { it in baseMetadata.selectedAttributes }.map {
            baseMetadata.copy(selectedAttributes = setOf(it))
        }
    }

    private fun getAttributesFromAnyPattern(resolver: Resolver, attributeSelectionPattern: AttributeSelectionPattern, patternWithFields: AnyPattern): List<AttributeSelectionMetadata> {
        val basePatternValues = patternWithFields.generateForEveryDiscriminatorValue(resolver)

        val baseMetadata = attributeSelectionPattern.toAttributeSelectionMetadata()
        val mandatoryKeyToDiscriminatorMetadata = mutableMapOf<String, MutableList<DiscriminatorMetadata>>()

        basePatternValues.forEach { (discriminatorMetadata, responseBodyValue) ->
            val mandatoryKeys = patternWithFields.eliminateOptionalKey(responseBodyValue, resolver).getTopLevelKeys()
            mandatoryKeys.filterNot { it in baseMetadata.selectedAttributes }.forEach { mandatoryKey ->
                mandatoryKeyToDiscriminatorMetadata.computeIfAbsent(mandatoryKey) {
                    mutableListOf()
                }.add(discriminatorMetadata)
            }
        }

        return mandatoryKeyToDiscriminatorMetadata.map { (mandatoryKey, discriminatorMetadata) ->
            baseMetadata.copy(discriminatorMetadata = discriminatorMetadata, selectedAttributes = setOf(mandatoryKey))
        }
    }

    private fun AttributeSelectionPattern.toAttributeSelectionMetadata(): AttributeSelectionMetadata {
        return AttributeSelectionMetadata(
            discriminatorMetadata = emptyList(),
            attributeSelectionField = this.queryParamKey,
            selectedAttributes = this.defaultFields.toSet()
        )
    }

    private fun Value.getTopLevelKeys(): Set<String> {
        return when(this) {
            is JSONObjectValue -> this.jsonObject.keys
            is JSONArrayValue -> this.list.firstOrNull()?.getTopLevelKeys() ?: emptySet()
            else -> emptySet()
        }
    }
}