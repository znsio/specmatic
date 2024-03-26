package `in`.specmatic.core.pattern

class HasValue<T>(override val value: T): ReturnValue<T> {
    override fun <U> withValue(default: U, fn: (T) -> U): U {
        return fn(value)
    }

    override fun <U> ifValue(fn: (T) -> U): ReturnValue<U> {
        return try {
            HasValue(fn(value))
        } catch(t: Throwable) {
            HasException(t)
        }
    }

    override fun <U> sequenceOf(fn: (T) -> Sequence<ReturnValue<U>>): Sequence<ReturnValue<U>> {
        return fn(value)
    }

    override fun update(fn: (T) -> T): ReturnValue<T> {
        return try {
            val newValue = fn(value)
            HasValue(newValue)
        } catch(t: Throwable) {
            HasException(t)
        }
    }

    override fun <U> combineWith(valueResult: ReturnValue<U>, fn: (T, U) -> T): ReturnValue<T> {
        if(valueResult is ReturnFailure)
            return valueResult.cast<T>()

        valueResult as HasValue<U>

        return try {
            val newValue = fn(value, valueResult.value)
            HasValue(newValue)
        } catch(t: Throwable) {
            HasException(t)
        }
    }
}
