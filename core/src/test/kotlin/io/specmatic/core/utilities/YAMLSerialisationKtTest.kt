package io.specmatic.core.utilities

import io.specmatic.core.pattern.*
import io.specmatic.core.value.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class YAMLSerialisationKtTest {

    @Test
    fun `should parse a YAML string with a UTF-8 BOM`() {
        val jsonContent = "\uFEFFgreeting: hello"
        assertDoesNotThrow { yamlStringToValue(jsonContent) }
    }

    @Test
    fun `should be able to parse yaml map to json-object value`() {
        val yamlContent = """
        key: value
        number: 123
        """.trimIndent()
        val value = yamlStringToValue(yamlContent)

        assertThat(value).isInstanceOf(JSONObjectValue::class.java); value as JSONObjectValue
        assertThat(value.jsonObject).containsExactlyInAnyOrderEntriesOf(mapOf(
            "key" to StringValue("value"),
            "number" to NumberValue(123),
        ))
    }

    @Test
    fun `should be able to parse yaml array to json-array value`() {
        val yamlContent = """
        - hello
        - 123
        """.trimIndent()
        val value = yamlStringToValue(yamlContent)

        assertThat(value).isInstanceOf(JSONArrayValue::class.java); value as JSONArrayValue
        assertThat(value.list).containsExactlyInAnyOrder(StringValue("hello"), NumberValue(123))
    }

    @Test
    fun `should be able to parse yaml content with start and end signals`() {
        val yamlContent = """
        ---
        key: value
        number: 123
        ...
        """.trimIndent()
        val value = yamlStringToValue(yamlContent)

        assertThat(value).isInstanceOf(JSONObjectValue::class.java); value as JSONObjectValue
        assertThat(value.jsonObject).containsExactlyInAnyOrderEntriesOf(mapOf(
            "key" to StringValue("value"),
            "number" to NumberValue(123),
        ))
    }

    @ParameterizedTest
    @MethodSource("primitiveToExpectedValueProvider")
    fun `should be able to parse primitive to expected value`(primitive: Any?, expectedValue: Value) {
        val mapValue = """key: $primitive"""
        val parsedValue = yamlStringToValue(mapValue)

        assertThat(parsedValue).isInstanceOf(JSONObjectValue::class.java); parsedValue as JSONObjectValue
        assertThat(parsedValue.jsonObject["key"]).isEqualTo(expectedValue)
    }

    @Test
    fun `should be able to serialise map value to yaml`() {
        val value = JSONObjectValue(mapOf("key" to StringValue("value")))
        val yamlString = valueToYamlString(value).trimEnd()

        assertThat(yamlString).isEqualTo("""key: value""")
    }

    @Test
    fun `should be able to serialise array value to yaml`() {
        val value = JSONArrayValue(listOf(StringValue("hello"), NumberValue(123)))
        val yamlString = valueToYamlString(value).trimEnd()

        assertThat(yamlString).isEqualTo("""
        - hello
        - 123
        """.trimIndent())
    }

    @ParameterizedTest
    @MethodSource("primitiveToExpectedValueProvider")
    fun `should be able to serialise primitive value to yaml`(primitive: Any?, expectedValue: Value) {
        val value = JSONObjectValue(mapOf("key" to expectedValue))
        val yamlString = valueToYamlString(value, mapper = unformattedAndQuotedYamlMapper).trimEnd()

        assertThat(yamlString).isEqualTo("""key: $primitive""")
    }

    @Test
    fun `should be able to parse patterns from yaml content`() {
        val yamlContent = """
        ---
        email?: (email)
        exact: 123
        details:
        - subKey?: (date)
        ...
        """.trimIndent()
        val parsedPattern = yamlStringToPattern(yamlContent)

        assertThat(parsedPattern).isInstanceOf(JSONObjectPattern::class.java); parsedPattern as JSONObjectPattern
        assertThat(parsedPattern.pattern).containsKeys("email?", "exact", "details")
        assertThat(parsedPattern.pattern["email?"]).isInstanceOf(EmailPattern::class.java)
        assertThat(parsedPattern.pattern["exact"]).satisfies(
            { assertThat(it).isInstanceOf(ExactValuePattern::class.java) },
            { it as ExactValuePattern; assertThat(it.pattern).isEqualTo(NumberValue(123)) }
        )
        assertThat(parsedPattern.pattern["details"]).satisfies(
            { assertThat(it).isInstanceOf(JSONArrayPattern::class.java) },
            { it as JSONArrayPattern; assertThat(it.pattern).hasSize(1).hasOnlyElementsOfType(JSONObjectPattern::class.java) },
            {
                it as JSONArrayPattern
                assertThat((it.pattern[0] as JSONObjectPattern).pattern).containsKeys("subKey?")
                assertThat((it.pattern[0] as JSONObjectPattern).pattern["subKey?"]).isInstanceOf(DatePattern::class.java)
            }
        )
    }

    companion object {
        @JvmStatic
        fun primitiveToExpectedValueProvider(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("\"\"", EmptyString),
                Arguments.of("\"hello\"", StringValue("hello")),
                Arguments.of("\"123\"", StringValue("123")),
                Arguments.of(123, NumberValue(123)),
                Arguments.of(100.5, NumberValue(100.5)),
                Arguments.of(true, BooleanValue(true)),
                Arguments.of(false, BooleanValue(false)),
                Arguments.of(null, NullValue)
            )
        }
    }
}