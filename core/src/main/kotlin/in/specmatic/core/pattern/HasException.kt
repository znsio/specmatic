package `in`.specmatic.core.pattern

import `in`.specmatic.core.Result
import `in`.specmatic.core.utilities.exceptionCauseMessage

class HasException<T>(val t: Throwable, val message: String = "", val breadCrumb: String? = null) : ReturnValue<T>, ReturnFailure {
    override fun <U> withDefault(default: U, fn: (T) -> U): U {
        return default
    }

    override fun <U> ifValue(fn: (T) -> U): ReturnValue<U> {
        return cast()

    }

    override fun <U> sequenceOf(fn: (T) -> Sequence<ReturnValue<U>>): Sequence<ReturnValue<U>> {
        return sequenceOf(cast())
    }

    override fun update(fn: (T) -> T): ReturnValue<T> {
        return this
    }

    override fun <U> combineWith(valueResult: ReturnValue<U>, fn: (T, U) -> T): ReturnValue<T> {
        return cast<T>()
    }

    override fun <V> cast(): ReturnValue<V> {
        return HasException<V>(t, message, breadCrumb)
    }

    override val value: T
        get() = throw t

    override fun addDetails(errorMessage: String, breadCrumb: String): ReturnValue<T> {
        val newE = toException(errorMessage, breadCrumb, toException())

        return HasException<T>(newE)
    }

    private fun toException(): Throwable {
        return toException(message, breadCrumb ?: "", t)
    }

    private fun toException(
        errorMessage: String,
        breadCrumb: String,
        t: Throwable
    ): Throwable {
        val newE = when (t) {
            is ContractException -> ContractException(errorMessage, breadCrumb, t, t.scenario, t.isCycle)
            else -> ContractException(errorMessage, breadCrumb, t)
        }

        return newE
    }

    override fun <U> realise(hasValue: (T) -> U, orFailure: (HasFailure<T>) -> U, orException: (HasException<T>) -> U): U {
        return orException(this)
    }
}
