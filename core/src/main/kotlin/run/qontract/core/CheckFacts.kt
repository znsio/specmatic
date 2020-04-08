package run.qontract.core

import run.qontract.core.value.Value

data class CheckFacts(private val state: Map<String, Value> = emptyMap()) : FactStore {
    override fun match(sampleValue: Value, key: String): Result =
            when (val stateValue = state.getValue(key)) {
                sampleValue -> Result.Success()
                else -> Result.Failure(message = "Server state did not match. Expected $stateValue at $key, but got $sampleValue.")
            }

    override fun has(key: String): Boolean = state.containsKey(key)

    override fun get(key: String): Value = state.getValue(key)
}
