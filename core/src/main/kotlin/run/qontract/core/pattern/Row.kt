package run.qontract.core.pattern

import run.qontract.core.References
import run.qontract.core.breakIntoPartsMaxLength

interface RowValue {
    fun fetch(): String
}

data class SimpleValue(val data: String): RowValue {
    override fun fetch(): String = data
}

class ReferenceValue(val value: String, private val references: Map<String, References> = emptyMap()): RowValue {
    override fun fetch(): String {
        val parts = breakIntoPartsMaxLength(withoutPatternDelimiters(value).trim().removePrefix("="), "\\." ,2)
        if(parts.size <= 1)
            throw ContractException("A reference to values must be of the form \"value-name.variable-set-by-contract\"")

        val valueName = parts[0]
        val selector = parts[1]

        return references[valueName]?.lookup(selector) ?: throw ContractException("Could not find reference to value \"$value\"")
    }
}

class ContextValue(val value: String, private val variables: Map<String, String> = emptyMap()): RowValue {
    override fun fetch(): String {
        val name = withoutPatternDelimiters(value).trim().removePrefix("=")

        return variables[name] ?: throw ContractException("Context did not contain a value named $name")
    }
}

data class Row(val columnNames: List<String> = emptyList(), val values: List<String> = emptyList(), val variables: Map<String, String> = emptyMap(), val references: Map<String, References> = emptyMap()) {
    private val cells = columnNames.zip(values.map { it }).toMap().toMutableMap()

    fun getField(columnName: String): String {
        return getValue(columnName).fetch()
    }

    // TODO the constructor should handle the when block and convert all row values
    //  This function should only fetch and forward the call
    private fun getValue(columnName: String): RowValue {
        val value = cells.getValue(columnName)

        return when {
            isContextValue(value) && isReferenceValue(value) -> ReferenceValue(value, references)
            isContextValue(value) -> ContextValue(value, variables)
            else -> SimpleValue(value)
        }
    }

    private fun isReferenceValue(value: String): Boolean = value.contains(".")

    private fun isContextValue(value: String) =
        isPatternToken(value) && withoutPatternDelimiters(value).trim().startsWith("=")

    fun containsField(key: String): Boolean = cells.containsKey(key)
}
