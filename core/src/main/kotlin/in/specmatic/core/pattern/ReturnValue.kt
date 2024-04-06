package `in`.specmatic.core.pattern

import `in`.specmatic.core.Result

sealed interface ReturnValue<T> {
    val value: T

    abstract fun <U> withDefault(default: U, fn: (T) -> U): U
    abstract fun <U> ifValue(fn: (T) -> U): ReturnValue<U>
    fun <U> sequenceOf(fn: (T) -> Sequence<ReturnValue<U>>): Sequence<ReturnValue<U>>
    fun update(fn: (T) -> T): ReturnValue<T>
    fun <U> combineWith(valueResult: ReturnValue<U>, fn: (T, U) -> T): ReturnValue<T>
    fun <U> realise(hasValue: (T) -> U, orFailure: (HasFailure<T>) -> U, orException: (HasException<T>) -> U): U
    fun addDetails(errorMessage: String, breadCrumb: String): ReturnValue<T>
}

fun <ReturnType> returnValue(errorMessage: String = "", breadCrumb: String = "", f: ()->Sequence<ReturnValue<ReturnType>>): Sequence<ReturnValue<ReturnType>> {
    return try {
        f().map { it.addDetails(errorMessage, breadCrumb) }
    }
    catch(contractException: ContractException) {
        val failure =
            Result.Failure(message = errorMessage, breadCrumb = breadCrumb, cause = contractException.failure())
        sequenceOf(HasFailure(failure))
    }
    catch(throwable: Throwable) {
        sequenceOf(HasException(throwable, errorMessage, breadCrumb))
    }
}

