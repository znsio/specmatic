package io.specmatic.core

import io.specmatic.core.value.Value

interface FactStore {
    fun match(sampleValue: Value, key: String): Result
    fun has(key: String): Boolean
    fun get(key: String): Value
}