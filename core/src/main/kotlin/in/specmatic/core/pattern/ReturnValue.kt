package `in`.specmatic.core.pattern

sealed interface ReturnValue<T> {
    val value: T

    abstract fun <U> withDefault(default: U, fn: (T) -> U): U
    abstract fun <U> ifValue(fn: (T) -> U): ReturnValue<U>
    fun <U> sequenceOf(fn: (T) -> Sequence<ReturnValue<U>>): Sequence<ReturnValue<U>>
    fun update(fn: (T) -> T): ReturnValue<T>
    fun <U> combineWith(valueResult: ReturnValue<U>, fn: (T, U) -> T): ReturnValue<T>
    fun <U> realise(hasValue: (T) -> U, orFailure: (HasFailure<T>) -> U, orException: (HasException<T>) -> U): U
}

fun <T, U> Sequence<ReturnValue<T>>.flatMap(fn: (T) -> Sequence<ReturnValue<U>>): Sequence<ReturnValue<U>> {
    val iterator = this.iterator()

    return sequence {
        while(iterator.hasNext()) {
            val next = iterator.next()

            if(next is HasValue<*>) {
                val output = fn(next.value)

                yieldAll(output)
            }

            if(next is ReturnFailure)
                yield(next.cast())
        }
    }
}

fun <T, U> List<ReturnValue<T>>.list(fn: (List<T>) -> Sequence<ReturnValue<U>>): Sequence<ReturnValue<U>> {
    val initial: ReturnValue<List<T>> = HasValue(emptyList<T>())

    val patterns: ReturnValue<List<T>> = this.fold(initial) { _acc, _pattern ->
        _acc.combineWith(_pattern) { acc, pattern ->
            acc.plus(pattern)
        }
    }

    return patterns.sequenceOf {
        fn(it)
    }
}

fun <T, U> Sequence<ReturnValue<T>>.seq(fn: (Sequence<T>) -> Sequence<ReturnValue<U>>): Sequence<ReturnValue<U>> {
    val initial: ReturnValue<Sequence<T>> = HasValue(emptySequence<T>())

    val patterns: ReturnValue<Sequence<T>> = this.fold(initial) { _acc, _pattern ->
        _acc.combineWith(_pattern) { acc, pattern ->
            acc.plus(pattern)
        }
    }

    return patterns.sequenceOf {
        fn(it)
    }
}
