package io.specmatic.core

import io.specmatic.core.pattern.Pattern
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.Value

object PatternBasedAttributeSelectionGenerator {
    fun getAttributeFromPattern(resolver: Resolver, attributeSelectionPattern: AttributeSelectionPattern, patternWithFields: Pattern): List<AttributeSelectionMetadata> {
        val basePatternValue = patternWithFields.generate(resolver)
        val mandatoryKeys = patternWithFields.eliminateOptionalKey(basePatternValue, resolver).getTopLevelKeys()
        val baseMetadata = attributeSelectionPattern.toAttributeSelectionMetadata()
        return mandatoryKeys.filterNot { it in baseMetadata.selectedAttributes }.map {
            baseMetadata.copy(selectedAttributes = setOf(it))
        }
    }

    private fun AttributeSelectionPattern.toAttributeSelectionMetadata(): AttributeSelectionMetadata {
        return AttributeSelectionMetadata(
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