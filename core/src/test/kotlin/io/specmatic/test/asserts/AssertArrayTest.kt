package io.specmatic.test.asserts

import io.specmatic.core.Result
import io.specmatic.core.pattern.ContractException
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.StringValue
import io.specmatic.test.asserts.AssertComparisonTest.Companion.toFactStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class AssertArrayTest {
    @ParameterizedTest
    @CsvSource(
        "REQUEST.BODY, [*]name, ENTITY.name, array_has",
        "REQUEST.BODY[*], name, ENTITY.name, array_has",
    )
    fun `should be able to parse equal and not equal assertions`(prefix: String, key: String, lookupKey: String, assertType: String) {
        val value = StringValue("\$${assertType}($lookupKey)")

        println("value: $value, prefix: $prefix, key: $key, lookupKey: $lookupKey, assertType: $assertType")
        val assert = parsedAssert(prefix, key, value)
        assertThat(assert).isNotNull.isInstanceOf(AssertArray::class.java); assert as AssertArray
        assertThat(assert.keys).containsExactly("REQUEST", "BODY", "[*]", "name")
        assertThat(assert.lookupKey).isEqualTo(lookupKey)
        assertThat(assert.arrayAssertType).isEqualTo(ArrayAssertType.ARRAY_HAS)
    }

    @Test
    fun `should return success when array contains expected value`() {
        val assert = AssertArray(keys = listOf("BODY", "[*]", "name"), lookupKey = "ENTITY.name", arrayAssertType = ArrayAssertType.ARRAY_HAS)

        val actualStore = mapOf("ENTITY.name" to StringValue("John"))
        val bodyValue = JSONArrayValue(
            listOf(
                JSONObjectValue(mapOf("name" to StringValue("Jane"))),
                JSONObjectValue(mapOf("name" to StringValue("John"))),
                JSONObjectValue(mapOf("name" to StringValue("May")))
            )
        )
        val currentStore = bodyValue.toFactStore("BODY")
        val result = assert.assert(currentStore, actualStore)

        assertThat(result).withFailMessage(result.reportString()).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should return failure when array does not contain expected value`() {
        val assert = AssertArray(keys = listOf("BODY", "[*]", "name"), lookupKey = "ENTITY.name", arrayAssertType = ArrayAssertType.ARRAY_HAS)

        val actualStore = mapOf("ENTITY.name" to StringValue("John"))
        val bodyValue = JSONArrayValue(
            listOf(
                JSONObjectValue(mapOf("name" to StringValue("Jane"))),
                JSONObjectValue(mapOf("name" to StringValue("May")))
            )
        )
        val currentStore = bodyValue.toFactStore("BODY")

        val result = assert.assert(currentStore, actualStore)
        println(result.reportString())

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat(result.reportString()).containsIgnoringWhitespaces("""
        >> BODY[*].name
        None of the values matched "ENTITY.name" of value "John"
        """.trimIndent())
    }

    @Test
    fun `should return failure when the value is not an array`() {
        val assert = AssertArray(keys = listOf("BODY", "[*]", "name"), lookupKey = "ENTITY.name", arrayAssertType = ArrayAssertType.ARRAY_HAS)

        val actualStore = mapOf("ENTITY.name" to StringValue("John"))
        val bodyValue = JSONObjectValue(mapOf("name" to StringValue("Jane")))
        val currentStore = bodyValue.toFactStore("BODY")

        val result = assert.assert(currentStore, actualStore)
        println(result.reportString())

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat(result.reportString()).containsIgnoringWhitespaces("""
        >> BODY[*]
        Could not resolve "BODY[*]" in response
        """.trimIndent())
    }

    @Test
    fun `should throw exception when used on non-array base`() {
        val exception = assertThrows<ContractException> {
            AssertArray(keys = listOf("BODY", "name"), lookupKey = "ENTITY.name", arrayAssertType = ArrayAssertType.ARRAY_HAS)
        }

        assertThat(exception.report()).isEqualToNormalizingWhitespace("""
        >> BODY.name
        Array Asserts can only be used on arrays
        """.trimIndent())
    }

    @Test
    fun `should be able to create dynamic asserts based on keys`() {
        val arrayAssert = AssertArray(
            keys = listOf("BODY", "[*]", "details", "[*]", "name"),
            lookupKey = "ENTITY.name",
            arrayAssertType = ArrayAssertType.ARRAY_HAS
        )

        val value = JSONArrayValue(List(3) {
            JSONObjectValue(mapOf(
                "details" to JSONArrayValue(List(2) {
                    JSONObjectValue(mapOf("name" to StringValue("John")))
                })
            ))
        })
        val dynamicAsserts = arrayAssert.dynamicAsserts(value.toFactStore("BODY"))

        assertThat(dynamicAsserts.size).isEqualTo(3)
        dynamicAsserts.forEachIndexed { index, it ->
            assertThat(it).isInstanceOf(AssertArray::class.java); it as AssertArray
            assertThat(it.keys).containsExactly("BODY", "[$index]", "details", "[*]", "name")
            assertThat(it.lookupKey).isEqualTo("ENTITY.name")
            assertThat(it.arrayAssertType).isEqualTo(ArrayAssertType.ARRAY_HAS)
        }
    }
}