package `in`.specmatic.core.pattern

import `in`.specmatic.core.Scenario
import `in`.specmatic.test.ScenarioTestGenerationFailure

sealed interface ReturnValue<T> {
    val value: T

    abstract fun <U> withValue(default: U, fn: (T) -> U): U
    abstract fun <U> ifValue(fn: (T) -> U): ReturnValue<U>
    fun <U> sequenceOf(fn: (T) -> Sequence<ReturnValue<U>>): Sequence<ReturnValue<U>>
    fun update(fn: (T) -> T): ReturnValue<T>
    fun <U> combineWith(valueResult: ReturnValue<U>, fn: (T, U) -> T): ReturnValue<T>
    fun <U> reify(hasValue: (T) -> U, orElse: () -> U): U
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
        }
    }

}
