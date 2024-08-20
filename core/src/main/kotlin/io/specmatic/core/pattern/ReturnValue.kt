package io.specmatic.core.pattern

import io.specmatic.core.Result

sealed interface ReturnValue<T> {
    val value: T

    abstract fun <U> withDefault(default: U, fn: (T) -> U): U

    fun <U, V> withDefault(default: U, other: ReturnValue<V>, fn: (T, V) -> U): U {
        if(this !is HasValue && other !is HasValue)
            return default

        return fn(this.value, other.value)
    }

    abstract fun <U> ifValue(fn: (T) -> U): ReturnValue<U>
    abstract fun <U> ifHasValue(fn: (HasValue<T>) -> ReturnValue<U>): ReturnValue<U>
    fun update(fn: (T) -> T): ReturnValue<T>
    fun <U> assimilate(acc: ReturnValue<U>, fn: (T, U) -> T): ReturnValue<T>
    fun <U, V> combine(acc: ReturnValue<U>, fn: (T, U) -> V): ReturnValue<V>
    fun <U> realise(hasValue: (T, String?) -> U, orFailure: (HasFailure<T>) -> U, orException: (HasException<T>) -> U): U
    fun addDetails(message: String, breadCrumb: String): ReturnValue<T>
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

fun <K, ValueType> Map<K, ReturnValue<ValueType>>.mapFold(): ReturnValue<Map<K, ValueType>> {
    val initial: ReturnValue<Map<K, ValueType>> = HasValue<Map<K, ValueType>>(emptyMap())

    return this.entries.fold(initial) { accR: ReturnValue<Map<K, ValueType>>, (key: K, valueR: ReturnValue<ValueType>) ->
        accR.assimilate(valueR) { acc, value ->
            acc.plus(key to value)
        }
    }
}

fun <ValueType> List<ReturnValue<ValueType>>.listFold(): ReturnValue<List<ValueType>> {
    val initial: ReturnValue<List<ValueType>> = HasValue<List<ValueType>>(emptyList())

    return this.fold(initial) { accR: ReturnValue<List<ValueType>>, valueR: ReturnValue<ValueType> ->
        accR.assimilate(valueR) { acc, value ->
            acc.plus(value)
        }
    }
}

fun <T> Sequence<List<ReturnValue<out T>>>.sequenceListFold(): Sequence<ReturnValue<List<T>>> {
    val data: Sequence<List<ReturnValue<out T>>> = this

    return data.map { listOfReturnValues ->
        val error: ReturnValue<out T>? = listOfReturnValues.firstOrNull { it !is HasValue }

        if (error != null)
            listOf(error)

        val valueDetails: List<ValueDetails> = listOfReturnValues.map { (it as HasValue).valueDetails }.flatten()
        HasValue(listOfReturnValues.map { it.value }, valueDetails)
    }
}

fun <T> Sequence<ReturnValue<T>>.foldIntoReturnValueOfSequence(): ReturnValue<Sequence<T>> {
    val init: ReturnValue<Sequence<T>> = HasValue(emptySequence<T>())

    return this.fold(init) { acc: ReturnValue<Sequence<T>>, item: ReturnValue<T> ->
        acc.assimilate(item) { accValue, itemValue ->
            accValue.plus(itemValue)
        }
    }
}

fun <T> ReturnValue<T>.breadCrumb(breadCrumb: String?): ReturnValue<T> {
    if(breadCrumb == null)
        return this

    return this.addDetails("", breadCrumb)
}

fun returnValueSequence(fn: () -> Sequence<ReturnValue<Pattern>>): Sequence<ReturnValue<Pattern>> {
    return try {
        fn()
    } catch(t: Throwable) {
        return sequenceOf(HasException(t, ""))
    }
}

fun <T> Sequence<ReturnValue<T>>.filterValueIsNot(fn: (T) -> Boolean): Sequence<ReturnValue<T>> {
    return this.filterNot { returnValue ->
        returnValue.withDefault(false) { value ->
            fn(value)
        }
    }
}

fun Sequence<ReturnValue<List<List<Pattern>>>>.foldToSequenceOfReturnValueList(): Sequence<ReturnValue<List<Pattern>>> {
    val seq = this

    return sequence {
        seq.forEach { returnValue: ReturnValue<List<List<Pattern>>> ->
            if (returnValue is HasValue) {
                returnValue.value.forEach { list ->
                    yield(HasValue(list, returnValue.valueDetails))
                }
            } else if (returnValue is ReturnFailure) {
                yield(returnValue.cast())
            }
        }
    }

}

fun <T> exception(fn: () -> T): Throwable? {
    return try {
        fn()
        null
    } catch(t: Throwable) {
        t
    }
}