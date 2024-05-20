package `in`.specmatic.core.pattern

import `in`.specmatic.core.Result

data class HasFailure<T>(val failure: Result.Failure, val message: String = "") : ReturnValue<T>, ReturnFailure {
    override fun <U> withDefault(default: U, fn: (T) -> U): U {
        return default
    }

    override fun <U> ifValue(fn: (T) -> U): ReturnValue<U> {
        return HasFailure<U>(failure)
    }

    override fun update(fn: (T) -> T): ReturnValue<T> {
        return this
    }

    override fun <U> combineWith(valueResult: ReturnValue<U>, fn: (T, U) -> T): ReturnValue<T> {
        return cast<T>()
    }

    override fun <U> cast(): ReturnValue<U> {
        return HasFailure<U>(failure, message)
    }

    override val value: T
        get() = throw ContractException(failure.toFailureReport())

    override fun <U> ifHasValue(fn: (HasValue<T>) -> ReturnValue<U>): ReturnValue<U> {
        return cast()
    }

    fun toFailure(): Result.Failure {
        return Result.Failure(message, failure)
    }

    override fun addDetails(message: String, breadCrumb: String): ReturnValue<T> {
        return HasFailure<T>(Result.Failure(message, this.toFailure(), breadCrumb))
    }

    override fun <U> realise(hasValue: (T, String?) -> U, orFailure: (HasFailure<T>) -> U, orException: (HasException<T>) -> U): U {
        return orFailure(this)
    }
}
