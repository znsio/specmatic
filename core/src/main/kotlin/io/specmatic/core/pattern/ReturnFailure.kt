package io.specmatic.core.pattern

import io.specmatic.core.Result

interface ReturnFailure {
    fun <T> cast(): ReturnValue<T>
    fun toFailure(): Result.Failure
}
