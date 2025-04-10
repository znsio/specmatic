package io.specmatic.core.utilities

import io.specmatic.core.pattern.parsedJSONObject
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test

class JSONSerialisationKtTest {
    @Test
    fun `should parse a JSON string with a UTF-8 BOM`() {
        val jsonContent = "\uFEFF{\"greeting\":\"hello\"}"
        assertDoesNotThrow { parsedJSONObject(jsonContent) }
    }
}