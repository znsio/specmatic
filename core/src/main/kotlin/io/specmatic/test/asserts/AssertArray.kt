package io.specmatic.test.asserts

import io.specmatic.core.Result
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.Value

class AssertArray: Assert {
    override fun assert(currentFactStore: Map<String, Value>, actualFactStore: Map<String, Value>): Result {
        TODO("Not yet implemented")
    }

    private fun assert(value: JSONObjectValue, factStore: Map<String, Value>): Result {
        TODO("Not yet implemented")
    }

    private fun assert(value: JSONArrayValue, factStore: Map<String, Value>): Result {
        TODO("Not yet implemented")
    }

    companion object {
        fun parse(prefix: String, key: String, value: Value): AssertArray? {
            return null
        }
    }
}