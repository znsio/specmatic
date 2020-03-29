package run.qontract.core.pattern

class Row constructor(columnNames: List<String> = mutableListOf(), values: List<String> = mutableListOf()) {
    private val cells = columnNames.zip(values.map { it }).toMap().toMutableMap()

    fun getField(columnName: String): Any = cells.getValue(columnName)
    fun containsField(key: String): Boolean = cells.containsKey(key)
}
