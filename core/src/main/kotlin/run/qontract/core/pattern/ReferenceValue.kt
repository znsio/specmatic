package run.qontract.core.pattern

import run.qontract.core.References
import run.qontract.core.breakIntoPartsMaxLength

class ReferenceValue(val value: String, private val references: Map<String, References> = emptyMap()): RowValue {
    override fun fetch(): String {
        val parts = breakIntoPartsMaxLength(withoutPatternDelimiters(value).trim().removePrefix("="), "\\.", 2)
        if(parts.size <= 1)
            throw ContractException("A reference to values must be of the form \"value-name.variable-set-by-contract\"")

        val valueName = parts[0]
        val selector = parts[1]

        return references[valueName]?.lookup(selector) ?: throw ContractException("Could not find reference to value \"$value\"")
    }
}