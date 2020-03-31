package run.qontract.core.pattern

data class Row constructor(val columnNames: List<String> = emptyList(), val values: List<String> = emptyList()) {
    private val cells = columnNames.zip(values.map { it }).toMap().toMutableMap()

    fun getField(columnName: String): Any = cells.getValue(columnName)
    fun containsField(key: String): Boolean = cells.containsKey(key)
}
