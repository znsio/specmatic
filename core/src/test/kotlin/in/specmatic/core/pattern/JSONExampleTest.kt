package `in`.specmatic.core.pattern

import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.StringValue
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class JSONExampleTest {
    @Test
    fun `has scalar value for key`() {
        val jsonExample = JSONExample(JSONObjectValue(mapOf("key" to StringValue("value"))), Row())
        assertTrue(jsonExample.hasScalarValueForKey("key"))
    }

    @Test
    fun `does not have scalar value for key`() {
        val jsonExample = JSONExample(JSONObjectValue(mapOf("key" to JSONObjectValue(mapOf("key" to StringValue("value"))))), Row())
        assertFalse(jsonExample.hasScalarValueForKey("key"))
    }

    @Test
    fun `get value from top level keys`() {
        val jsonExample = JSONExample(JSONObjectValue(mapOf("key" to StringValue("value"))), Row())
        assertEquals("value", jsonExample.getValueFromTopLevelKeys("key"))
    }

    @Test
    fun `get value from top level keys throws exception when example is not a JSON object`() {
        val jsonExample = JSONExample(JSONArrayValue(listOf(StringValue("value"))), Row())
        assertThrows(ContractException::class.java) {
            jsonExample.getValueFromTopLevelKeys("key")
        }
    }
}