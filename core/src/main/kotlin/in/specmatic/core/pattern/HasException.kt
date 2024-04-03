package `in`.specmatic.core.pattern

import `in`.specmatic.core.Result
import `in`.specmatic.core.utilities.exceptionCauseMessage

class HasException<T>(val t: Throwable, val message: String = "", val breadCrumb: String? = null) : ReturnValue<T>, ReturnFailure {
    override fun <U> withDefault(default: U, fn: (T) -> U): U {
        return default
    }

    override fun <U> ifValue(fn: (T) -> U): ReturnValue<U> {
        return HasException<U>(t)

    }

    override fun <U> sequenceOf(fn: (T) -> Sequence<ReturnValue<U>>): Sequence<ReturnValue<U>> {
        return sequenceOf(HasException<U>(t))
    }

    override fun update(fn: (T) -> T): ReturnValue<T> {
        return this
    }

    override fun <U> combineWith(valueResult: ReturnValue<U>, fn: (T, U) -> T): ReturnValue<T> {
        return cast<T>()
    }

    override fun <V> cast(): ReturnValue<V> {
        return HasException<V>(t)
    }

    override val value: T
        get() = throw t

    override fun <U> realise(hasValue: (T) -> U, orFailure: (HasFailure<T>) -> U, orException: (HasException<T>) -> U): U {
        return orException(this)
    }
}
