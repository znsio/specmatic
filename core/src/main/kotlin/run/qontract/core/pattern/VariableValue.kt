package run.qontract.core.pattern

data class VariableValue(private val valueReference: ValueReference, private val variables: Map<String, String> = emptyMap()): RowValue {
    override fun fetch(): String {
        return variables[valueReference.name] ?: throw ContractException("Context did not contain a value named ${valueReference.name}")
    }
}