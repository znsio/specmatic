package io.specmatic.core.pattern

import io.specmatic.core.References
import io.specmatic.core.breakIntoPartsMaxLength

data class ReferenceValue(private val valueReference: ValueReference, private val references: Map<String, References> = emptyMap()): RowValue {
    override fun fetch(): String {
        val parts = breakUpIntoParts()
        if(parts.size <= 1)
            throw ContractException("A reference to values must be of the form \"value-name.variable-set-by-contract\"")

        val name = parts[0]
        val selector = parts[1]

        return references[name]?.lookup(selector) ?: throw ContractException("Could not find reference to value \"${valueReference.name}\"")
    }

    private fun breakUpIntoParts() =
        breakIntoPartsMaxLength(valueReference.name, "\\.", 2)
}