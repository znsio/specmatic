package io.specmatic.core

import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.NumberValue
import io.specmatic.core.value.StringValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DataRepresentationTests {

    @Test
    fun `should be able to convert flattened map to a nested structure`() {
        val initialMap = mapOf(
            "Schema.name" to StringValue("<NAME>"),
            "Schema.age" to NumberValue(10),
            "Schema.details.address" to JSONArrayValue(listOf("<ADDRESS1>", "<ADDRESS2>").map(::StringValue))
        )
        val dataRepresentation = DataRepresentation.from(initialMap)
        val finalValue = dataRepresentation.toValue()

        assertThat(finalValue.toStringLiteral()).isEqualToNormalizingWhitespace("""
        {
            "Schema": {
                "name": "<NAME>",
                "age": 10,
                "details": {
                    "address": [
                        "<ADDRESS1>",
                        "<ADDRESS2>"
                    ]
                }
            }
        }
        """.trimIndent())
    }
}