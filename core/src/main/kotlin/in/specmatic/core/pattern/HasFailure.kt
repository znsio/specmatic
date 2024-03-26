package `in`.specmatic.core.pattern

import `in`.specmatic.core.Result

class HasFailure<T>(val failure: Result.Failure) : ReturnValue<T>, ReturnFailure {
    override fun <U> withValue(default: U, fn: (T) -> U): U {
        return default
    }

    override fun <U> ifValue(fn: (T) -> U): ReturnValue<U> {
        return HasFailure<U>(failure)
    }

    override fun <U> sequenceOf(fn: (T) -> Sequence<ReturnValue<U>>): Sequence<ReturnValue<U>> {
        return sequenceOf(HasFailure<U>(failure))
    }

    override fun update(fn: (T) -> T): ReturnValue<T> {
        return this
    }

    override fun <U> combineWith(valueResult: ReturnValue<U>, fn: (T, U) -> T): ReturnValue<T> {
        return cast<T>()
    }

    override fun <U> cast(): ReturnValue<U> {
        return HasFailure<U>(failure)
    }

    override val value: T
        get() = throw ContractException(failure.toFailureReport())

    override fun <U> reify(hasValue: (T) -> U, orElse: () -> U): U {
        return orElse()
    }
}
