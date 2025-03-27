package io.specmatic.test.asserts

import io.specmatic.core.Resolver
import io.specmatic.core.Result
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.NullValue
import io.specmatic.core.value.Value

class AssertConditional(override val keys: List<String>, val conditionalAsserts: List<Assert>, val thenAsserts: List<Assert>, val elseAsserts: List<Assert>): Assert {
    override fun execute(currentFactStore: Map<String, Value>, actualFactStore: Map<String, Value>): Result {
        val mainResult = conditionalAsserts.executeAsserts(currentFactStore, actualFactStore)
        return when (mainResult) {
            is Result.Success -> thenAsserts.executeAsserts(currentFactStore, actualFactStore)
            else -> elseAsserts.executeAsserts(currentFactStore, actualFactStore)
        }
    }

    private fun List<Assert>.executeAsserts(currentFactStore: Map<String, Value>, actualFactStore: Map<String, Value>): Result {
        return Result.fromResults(map { it.execute(currentFactStore, actualFactStore) })
    }

    private fun collectDynamicAsserts(currentFactStore: Map<String, Value>, asserts: List<Assert>, dynamicPaths: List<List<String>>): List<List<Assert>> {
        if (asserts.isEmpty()) return emptyList()
        val dynamicAsserts = asserts.flatMap { it.dynamicAsserts(currentFactStore) { NullValue } }
        return dynamicPaths.map { path -> dynamicAsserts.filter { assert -> assert.keys.startsWith(path) } }
    }

    private fun List<String>.startsWith(prefix: List<String>): Boolean {
        return this.take(prefix.size) == prefix
    }

    override fun dynamicAsserts(currentFactStore: Map<String, Value>, ifNotExists: (String) -> Value): List<AssertConditional> {
        val dynamicPaths = generateDynamicPaths(keys, currentFactStore)
        val dynamicConditionalAsserts = collectDynamicAsserts(currentFactStore, conditionalAsserts, dynamicPaths)
        val dynamicThenAsserts = collectDynamicAsserts(currentFactStore, thenAsserts, dynamicPaths)
        val dynamicElseAsserts = collectDynamicAsserts(currentFactStore, elseAsserts, dynamicPaths)

        return dynamicPaths.mapIndexed { index, newKeys ->
            AssertConditional(
                keys = newKeys,
                conditionalAsserts = dynamicConditionalAsserts.getOrNull(index).orEmpty(),
                thenAsserts = dynamicThenAsserts.getOrNull(index).orEmpty(),
                elseAsserts = dynamicElseAsserts.getOrNull(index).orEmpty()
            )
        }
    }

    companion object {
        private fun toAsserts(keys: List<String>, jsonObjectValue: JSONObjectValue?, resolver: Resolver): List<Assert> {
            return jsonObjectValue?.jsonObject?.mapNotNull { (key, value) ->
                Assert.parse(keys + key, value, resolver)
            }.orEmpty()
        }

        fun parse(keys: List<String>, value: Value, resolver: Resolver): AssertConditional? {
            val jsonObject = value as? JSONObjectValue ?: return null

            val conditions = jsonObject.findFirstChildByPath("\$conditions") as? JSONObjectValue ?: return null
            val thenConditions = jsonObject.findFirstChildByPath("\$then") as? JSONObjectValue
            val elseConditions = jsonObject.findFirstChildByPath("\$else") as? JSONObjectValue

            if (thenConditions == null && elseConditions == null) return null
            val keysToConsider = keys.dropLast(1)
            return AssertConditional(
                keys = keysToConsider,
                conditionalAsserts = toAsserts(keysToConsider, conditions, resolver),
                thenAsserts = toAsserts(keysToConsider, thenConditions, resolver),
                elseAsserts = toAsserts(keysToConsider, elseConditions, resolver)
            )
        }
    }
}