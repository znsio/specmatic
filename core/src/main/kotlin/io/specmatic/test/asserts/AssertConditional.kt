package io.specmatic.test.asserts

import io.specmatic.core.Result
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.Value

class AssertConditional(override val prefix: String, val conditionalAsserts: List<Assert>, val thenAsserts: List<Assert>, val elseAsserts: List<Assert>): Assert {

    override fun assert(currentFactStore: Map<String, Value>, actualFactStore: Map<String, Value>): Result {
        val prefixValue = currentFactStore[prefix] ?: return Result.Failure(breadCrumb = prefix, message = "Could not resolve $prefix in current fact store")

        val dynamicAsserts = this.dynamicAsserts(prefixValue)
        val results = dynamicAsserts.map {
            val mainResult = it.conditionalAsserts.map { assert -> assert.assert(currentFactStore, actualFactStore) }.toResult()
            when (mainResult) {
                is Result.Success -> it.thenAsserts.map { assert ->  assert.assert(currentFactStore, actualFactStore) }.toResult()
                else -> it.elseAsserts.map { assert ->  assert.assert(currentFactStore, actualFactStore) }.toResult()
            }
        }

        return results.toResult()
    }

    private fun collectDynamicAsserts(prefixValue: Value, asserts: List<Assert>): Map<String, List<Assert>> {
        return asserts.flatMap { it.dynamicAsserts(prefixValue) }.groupBy { it.prefix }
    }

    override fun dynamicAsserts(prefixValue: Value): List<AssertConditional> {
        val newConditionalAsserts = collectDynamicAsserts(prefixValue, conditionalAsserts)
        val newThenAsserts = collectDynamicAsserts(prefixValue, thenAsserts)
        val newElseAsserts = collectDynamicAsserts(prefixValue, elseAsserts)

        return newConditionalAsserts.keys.map { prefix ->
            AssertConditional(
                prefix = prefix,
                conditionalAsserts = newConditionalAsserts[prefix].orEmpty(),
                thenAsserts = newThenAsserts[prefix].orEmpty(),
                elseAsserts = newElseAsserts[prefix].orEmpty()
            )
        }
    }

    override val key: String = ""

    companion object {
        private fun toAsserts(prefix: String, jsonObjectValue: JSONObjectValue?): List<Assert> {
            return jsonObjectValue?.jsonObject?.entries?.mapNotNull { (key, value) ->
                parsedAssert(prefix, key, value)
            }.orEmpty()
        }

        fun parse(prefix: String, key: String, value: Value): AssertConditional? {
            val conditions = (value as? JSONObjectValue)?.findFirstChildByPath("\$conditions") as? JSONObjectValue ?: return null
            val thenConditions = (value as? JSONObjectValue)?.findFirstChildByPath("\$then") as? JSONObjectValue
            val elseConditions = (value as? JSONObjectValue)?.findFirstChildByPath("\$else") as? JSONObjectValue

            if (thenConditions == null && elseConditions == null) return null

            return AssertConditional(
                prefix = prefix,
                conditionalAsserts = toAsserts(prefix, conditions),
                thenAsserts = toAsserts(prefix, thenConditions), elseAsserts = toAsserts(prefix, elseConditions)
            )
        }
    }
}