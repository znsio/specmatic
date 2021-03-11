package run.qontract.core.pattern

import run.qontract.core.References

data class Row(val columnNames: List<String> = emptyList(), val values: List<String> = emptyList(), val variables: Map<String, String> = emptyMap(), val references: Map<String, References> = emptyMap()) {
    private val cells = columnNames.zip(values.map { it }).toMap().toMutableMap()

    fun getField(columnName: String): String {
        return getValue(columnName).fetch()
    }

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
