package io.specmatic.test.asserts

import io.specmatic.core.Resolver
import io.specmatic.core.Result
import io.specmatic.core.pattern.*
import io.specmatic.core.value.*
import io.specmatic.test.asserts.AssertComparisonTest.Companion.toFactStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource


class AssertPatternTest {
    @ParameterizedTest
    @CsvSource(
        "REQUEST.BODY, name, (string)",
        "REQUEST.BODY, name, (number)",
        "REQUEST.BODY, name, (anyvalue)"
    )
    fun `should be able to parse pattern type`(prefix: String, key: String, value: String) {
        val resolver = Resolver()
        val patternType = resolvedHop(parsedValue(value).exactMatchElseType(), resolver)

        println("value: $value, prefix: $prefix, key: $key, value: $value, patternType: ${patternType.javaClass.simpleName}")
        val assert = AssertPattern.parse(prefix, key, parsedValue(value))
        assertThat(assert).isNotNull.isInstanceOf(AssertPattern::class.java)
        assert as AssertPattern
        assertThat(assert.prefix).isEqualTo("REQUEST.BODY")
        assertThat(assert.key).isEqualTo(key)
        assertThat(resolvedHop(assert.pattern, resolver)).isInstanceOf(patternType::class.java)
    }

    @Test
    fun `should not parse values to ExactValue Pattern`() {
        val assert = AssertPattern.parse("REQUEST.BODY", "name", StringValue("john"))
        assertThat(assert).isNull()
    }

    @Test
    fun `should not parse composite values to AssertPattern`() {
        val compositeValue = parsedJSONObject("""
        {
            "key": "value",
            "key2": "value2"
        }
        """.trimIndent())
        val assert = AssertPattern.parse("REQUEST.BODY", "composite", compositeValue)
        println(assert)
        assertThat(assert).isNull()
    }

    @Test
    fun `should return success when value matches the pattern`() {
        val assert = AssertPattern(prefix = "REQUEST.BODY", key = "name", pattern = parsedPattern("john"))

        val bodyValue = JSONObjectValue(mapOf("name" to StringValue("john")))
        val currentStore = bodyValue.toFactStore("REQUEST.BODY")

        val result = assert.assert(currentStore, currentStore)
        println(result.reportString())
        assertThat(result).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should return failure when value does not match the pattern`() {
        val assert = AssertPattern(prefix = "REQUEST.BODY", key = "name", pattern = parsedPattern("(string)"))

        val bodyValue = JSONObjectValue(mapOf("name" to NumberValue(100)))
        val currentStore = bodyValue.toFactStore("REQUEST.BODY")

        val result = assert.assert(currentStore, currentStore)
        println(result.reportString())

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat(result.reportString()).containsIgnoringWhitespaces("""
        >> REQUEST.BODY.name
        Expected string, actual was 100 (number)
        """.trimIndent())
    }

    @Test
    fun `should be able to create dynamic asserts based on prefix value`() {
        val assert = AssertPattern(prefix = "REQUEST.BODY", key = "name", pattern = parsedPattern("(string)"))
        val jsonValue = JSONObjectValue(mapOf("name" to NumberValue(100)))
        val arrayValue = JSONArrayValue(List(3) { jsonValue })

        val arrayBasedAsserts = assert.dynamicAsserts(arrayValue)
        assertThat(arrayBasedAsserts.size).isEqualTo(3)
        arrayBasedAsserts.forEachIndexed { index, it ->
            assertThat(it).isInstanceOf(AssertPattern::class.java)
            it as AssertPattern
            assertThat(it.prefix).isEqualTo("REQUEST.BODY[$index]")
            assertThat(it.key).isEqualTo("name")
            assertThat(it.pattern).isInstanceOf(StringPattern::class.java)
        }

        val jsonBasedAsserts = assert.dynamicAsserts(jsonValue)
        assertThat(jsonBasedAsserts.size).isEqualTo(1)
        assertThat(jsonBasedAsserts).allSatisfy {
            assertThat(it).isInstanceOf(AssertPattern::class.java)
            it as AssertPattern
            assertThat(it.prefix).isEqualTo("REQUEST.BODY")
            assertThat(it.key).isEqualTo("name")
            assertThat(it.pattern).isInstanceOf(StringPattern::class.java)
        }
    }

    @Test
    fun `should assert all array values based on dynamic asserts when prefix value is an array`() {
        val assert = AssertPattern(prefix = "REQUEST.BODY", key = "name", pattern = parsedPattern("(string)"))

        val jsonValue = JSONObjectValue(mapOf("name" to NumberValue(100)))
        val arrayValue = JSONArrayValue(List(3) { jsonValue })
        val currentStore = arrayValue.toFactStore("REQUEST.BODY")

        val result = assert.assert(currentStore, currentStore)
        println(result.reportString())

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat(result.reportString()).containsIgnoringWhitespaces("""
        >> REQUEST.BODY[0].name
        Expected string, actual was 100 (number)
        >> REQUEST.BODY[1].name
        Expected string, actual was 100 (number)
        >> REQUEST.BODY[2].name
        Expected string, actual was 100 (number)
        """.trimIndent())
    }
}
