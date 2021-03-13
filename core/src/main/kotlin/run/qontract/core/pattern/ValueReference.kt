package run.qontract.core.pattern

data class ValueReference(private val valueName: String) {
    val name: String = withoutPatternDelimiters(valueName).trim().removePrefix(DEREFERENCE_PREFIX)

    override fun equals(other: Any?): Boolean {
        return other is ValueReference && other.name == this.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}