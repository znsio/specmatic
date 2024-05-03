package `in`.specmatic.core.pattern

interface ReturnFailure {
    fun <T> cast(): ReturnValue<T>
}
