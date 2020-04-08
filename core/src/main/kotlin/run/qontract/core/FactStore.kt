package run.qontract.core

import run.qontract.core.value.Value

interface FactStore {
    fun match(sampleValue: Value, key: String): Result
    fun has(key: String): Boolean
    fun get(key: String): Value
}