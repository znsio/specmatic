package io.specmatic.test.asserts

import io.specmatic.core.Result
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.Value

class AssertConditional(val conditionalAsserts: List<Assert>, val thenAsserts: List<Assert>, val elseAsserts: List<Assert>): Assert {

    override fun assert(currentFactStore: Map<String, Value>, actualFactStore: Map<String, Value>): Result {
        val mainResult = conditionalAsserts.map { it.assert(currentFactStore, actualFactStore) }.toResult()
        return when (mainResult) {
            is Result.Success -> thenAsserts.map { it.assert(currentFactStore, actualFactStore) }.toResult()
            else -> elseAsserts.map { it.assert(currentFactStore, actualFactStore) }.toResult()
        }
    }

    companion object {
        private fun toAsserts(prefix: String, jsonObjectValue: JSONObjectValue?): List<Assert> {
            return jsonObjectValue?.jsonObject?.entries?.mapNotNull { (key, value) ->
                parsedAssert("$prefix.${key}", key, value)
            }.orEmpty()
        }

        fun parse(prefix: String, key: String, value: Value): AssertConditional? {
            val conditions = (value as? JSONObjectValue)?.findFirstChildByPath("\$conditions") as? JSONObjectValue ?: return null
            val thenConditions = (value as? JSONObjectValue)?.findFirstChildByPath("\$then") as? JSONObjectValue
            val elseConditions = (value as? JSONObjectValue)?.findFirstChildByPath("\$else") as? JSONObjectValue

            if (thenConditions == null && elseConditions == null) return null

            return AssertConditional(
                conditionalAsserts = toAsserts(prefix, conditions),
                thenAsserts = toAsserts(prefix, thenConditions), elseAsserts = toAsserts(prefix, elseConditions)
            )
        }
    }
}