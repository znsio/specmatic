package `in`.specmatic.core.pattern

data class PairOfTypes(val first: String?, val second: String?) {
    fun hasNoNulls(): Boolean = first != null && second != null
}
