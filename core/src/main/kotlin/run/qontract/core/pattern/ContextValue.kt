package run.qontract.core.pattern

class ContextValue(val value: String, private val variables: Map<String, String> = emptyMap()): RowValue {
    override fun fetch(): String {
        val name = withoutPatternDelimiters(value).trim().removePrefix("=")

        return variables[name] ?: throw ContractException("Context did not contain a value named $name")
    }
}