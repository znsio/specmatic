package `in`.specmatic.core.pattern

import `in`.specmatic.core.References

const val DEREFERENCE_PREFIX = "$"

data class Row(val columnNames: List<String> = emptyList(), val values: List<String> = emptyList(), val variables: Map<String, String> = emptyMap(), val references: Map<String, References> = emptyMap()) {
    private val cells = columnNames.zip(values.map { it }).toMap().toMutableMap()

    fun stringForOpenAPIError(): String {
        return columnNames.zip(values).joinToString(", ") { (key, value) ->
            "$key=$value"
        }
    }

    fun getField(columnName: String): String {
        return getValue(columnName).fetch()
    }

    private fun getValue(columnName: String): RowValue {
        val value = cells.getValue(columnName)

        return when {
            isContextValue(value) && isReferenceValue(value) -> ReferenceValue(ValueReference(value), references)
            isContextValue(value) -> VariableValue(ValueReference(value), variables)
            else -> SimpleValue(value)
        }
    }

    private fun isReferenceValue(value: String): Boolean = value.contains(".")

    private fun isContextValue(value: String): Boolean {
        return isPatternToken(value) && withoutPatternDelimiters(value).trim().startsWith(DEREFERENCE_PREFIX)
    }

    fun containsField(key: String): Boolean = cells.containsKey(key)
}
