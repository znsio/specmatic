package io.specmatic.test.asserts

import io.specmatic.core.Result
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.NullValue
import io.specmatic.core.value.StringValue
import io.specmatic.test.asserts.AssertComparisonTest.Companion.toFactStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class AssertExistenceTest {
    @ParameterizedTest
    @CsvSource(
        "REQUEST.BODY.name, name, exists",
        "REQUEST.BODY,      name, not_exists",
        "REQUEST.BODY.name, name, is_null",
        "REQUEST.BODY,      name, is_not_null",
    )
    fun `should be able to parse assertions`(prefix: String, key: String, checkTypeString: String) {
        val checkType = ExistenceCheckType.fromString(checkTypeString)!!
        val value = StringValue("\$${checkType.value}()")

        println("value: $value, prefix: $prefix, key: $key, checkType: $checkType")
        val assert = AssertExistence.parse(prefix, key, value)
        assertThat(assert).isNotNull.isInstanceOf(AssertExistence::class.java)
        assertThat(assert!!.prefix).isEqualTo("REQUEST.BODY")
        assertThat(assert.key).isEqualTo(key)
        assertThat(assert.checkType).isEqualTo(checkType)
    }

    @Test
    fun `should return failure when actual value does not exist and check type is exists`() {
        val assert = AssertExistence(prefix = "REQUEST.BODY", key = "name", checkType = ExistenceCheckType.EXISTS)

        val bodyValue = JSONObjectValue(emptyMap())
        val currentStore = bodyValue.toFactStore("REQUEST.BODY")

        val result = assert.assert(currentStore, emptyMap())
        println(result.reportString())

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat(result.reportString()).containsIgnoringWhitespaces("""
        >> REQUEST.BODY.name
        Expected "REQUEST.BODY.name" to exist
        """.trimIndent())
    }

    @Test
    fun `should return failure when actual value exists and check type is not_exists`() {
        val assert = AssertExistence(prefix = "REQUEST.BODY", key = "name", checkType = ExistenceCheckType.NOT_EXISTS)

        val bodyValue = JSONObjectValue(mapOf("name" to StringValue("John")))
        val currentStore = bodyValue.toFactStore("REQUEST.BODY")

        val result = assert.assert(currentStore, emptyMap())
        println(result.reportString())

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat(result.reportString()).containsIgnoringWhitespaces("""
        >> REQUEST.BODY.name
        Expected "REQUEST.BODY.name" to not exist
        """.trimIndent())
    }

    @Test
    fun `should return failure when actual value is null and check type is is_not_null`() {
        val assert = AssertExistence(prefix = "REQUEST.BODY", key = "name", checkType = ExistenceCheckType.NOT_NULL)

        val bodyValue = JSONObjectValue(mapOf("name" to NullValue))
        val currentStore = bodyValue.toFactStore("REQUEST.BODY")

        val result = assert.assert(currentStore, emptyMap())
        println(result.reportString())

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat(result.reportString()).containsIgnoringWhitespaces("""
        >> REQUEST.BODY.name
        Expected "REQUEST.BODY.name" to not be null
        """.trimIndent())
    }

    @Test
    fun `should return failure when actual value is not null check type is is_null`() {
        val assert = AssertExistence(prefix = "REQUEST.BODY", key = "name", checkType = ExistenceCheckType.IS_NULL)

        val bodyValue = JSONObjectValue(mapOf("name" to StringValue("John")))
        val currentStore = bodyValue.toFactStore("REQUEST.BODY")

        val result = assert.assert(currentStore, emptyMap())
        println(result.reportString())

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat(result.reportString()).containsIgnoringWhitespaces("""
        >> REQUEST.BODY.name
        Expected "REQUEST.BODY.name" to be null
        """.trimIndent())
    }
}