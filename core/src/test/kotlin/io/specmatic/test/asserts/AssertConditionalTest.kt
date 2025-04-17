package io.specmatic.test.asserts

import io.specmatic.core.Resolver
import io.specmatic.core.Result
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.StringValue
import io.specmatic.test.asserts.AssertComparisonTest.Companion.toFactStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AssertConditionalTest {

    @Nested
    inner class JsonObjectPrefixValue {
        @Test
        fun `should execute then asserts when conditional asserts are met`() {
            val assert = AssertConditional(
                prefix = "REQUEST.BODY",
                conditionalAsserts = listOf(AssertComparison(prefix = "REQUEST.BODY", key = "name", lookupKey = "ENTITY.name", isEqualityCheck = true)),
                thenAsserts = listOf(AssertComparison(prefix = "REQUEST.BODY", key = "address", lookupKey = "ENTITY.address", isEqualityCheck = true)),
                elseAsserts = listOf(AssertComparison(prefix = "REQUEST.BODY", key = "address", lookupKey = "ENTITY.address", isEqualityCheck = false))
            )

            val actualStore = mapOf("ENTITY.name" to StringValue("John"), "ENTITY.address" to StringValue("123 Main St"))
            val bodyValue = JSONObjectValue(mapOf("name" to StringValue("John"), "address" to StringValue("456 Main St")))
            val currentStore = bodyValue.toFactStore("REQUEST.BODY")

            val result = assert.assert(currentStore, actualStore)
            println(result.reportString())

            assertThat(result).isInstanceOf(Result.Failure::class.java)
            assertThat(result.reportString()).isEqualToNormalizingWhitespace("""
            >> REQUEST.BODY.address
            Expected "456 Main St" to equal "123 Main St"
            """.trimIndent())
        }

        @Test
        fun `should execute else asserts when conditional asserts are not met`() {
            val assert = AssertConditional(
                prefix = "REQUEST.BODY",
                conditionalAsserts = listOf(AssertComparison(prefix = "REQUEST.BODY", key = "name", lookupKey = "ENTITY.name", isEqualityCheck = true)),
                thenAsserts = listOf(AssertComparison(prefix = "REQUEST.BODY", key = "address", lookupKey = "ENTITY.address", isEqualityCheck = true)),
                elseAsserts = listOf(AssertComparison(prefix = "REQUEST.BODY", key = "address", lookupKey = "ENTITY.address", isEqualityCheck = false))
            )

            val actualStore = mapOf("ENTITY.name" to StringValue("John"), "ENTITY.address" to StringValue("123 Main St"))
            val bodyValue = JSONObjectValue(mapOf("name" to StringValue("Jane"), "address" to StringValue("123 Main St")))
            val currentStore = bodyValue.toFactStore("REQUEST.BODY")

            val result = assert.assert(currentStore, actualStore)
            println(result.reportString())

            assertThat(result).isInstanceOf(Result.Failure::class.java)
            assertThat(result.reportString()).isEqualToNormalizingWhitespace("""
            >> REQUEST.BODY.address
            Expected "123 Main St" to not equal "123 Main St"
            """.trimIndent())
        }
    }

    @Nested
    inner class JsonArrayPrefixValue {
        @Test
        fun `should execute then asserts when conditional asserts are met`() {
            val assert = AssertConditional(
                prefix = "REQUEST.BODY",
                conditionalAsserts = listOf(AssertComparison(prefix = "REQUEST.BODY", key = "name", lookupKey = "ENTITY.name", isEqualityCheck = true)),
                thenAsserts = listOf(AssertComparison(prefix = "REQUEST.BODY", key = "address", lookupKey = "ENTITY.address", isEqualityCheck = true)),
                elseAsserts = listOf(AssertComparison(prefix = "REQUEST.BODY", key = "address", lookupKey = "ENTITY.address", isEqualityCheck = false))
            )

            val bodyValue = JSONArrayValue(
                listOf(
                    JSONObjectValue(mapOf("name" to StringValue("Jane"), "address" to StringValue("123 Main St"))),
                    JSONObjectValue(mapOf("name" to StringValue("John"), "address" to StringValue("123 Main St"))),
                    JSONObjectValue(mapOf("name" to StringValue("May"), "address" to StringValue("123 Main St")))
                )
            )
            val currentStore = bodyValue.toFactStore("REQUEST.BODY")
            val actualStore = mapOf("ENTITY.name" to StringValue("John"), "ENTITY.address" to StringValue("456 Main St"))

            val result = assert.assert(currentStore, actualStore)
            println(result.reportString())

            assertThat(result).isInstanceOf(Result.Failure::class.java)
            assertThat(result.reportString()).isEqualToNormalizingWhitespace("""
            >> REQUEST.BODY[1].address
            Expected "123 Main St" to equal "456 Main St"
            """.trimIndent())
        }

        @Test
        fun `should execute else asserts when conditional asserts are not met`() {
            val assert = AssertConditional(
                prefix = "REQUEST.BODY",
                conditionalAsserts = listOf(AssertComparison(prefix = "REQUEST.BODY", key = "name", lookupKey = "ENTITY.name", isEqualityCheck = true)),
                thenAsserts = listOf(AssertComparison(prefix = "REQUEST.BODY", key = "address", lookupKey = "ENTITY.address", isEqualityCheck = true)),
                elseAsserts = listOf(AssertComparison(prefix = "REQUEST.BODY", key = "address", lookupKey = "ENTITY.address", isEqualityCheck = false))
            )

            val bodyValue = JSONArrayValue(
                listOf(
                    JSONObjectValue(mapOf("name" to StringValue("Jane"), "address" to StringValue("123 Main St"))),
                    JSONObjectValue(mapOf("name" to StringValue("John"), "address" to StringValue("123 Main St"))),
                    JSONObjectValue(mapOf("name" to StringValue("May"), "address" to StringValue("123 Main St")))
                )
            )
            val currentStore = bodyValue.toFactStore("REQUEST.BODY")
            val actualStore = mapOf("ENTITY.name" to StringValue("John"), "ENTITY.address" to StringValue("123 Main St"))

            val result = assert.assert(currentStore, actualStore)
            println(result.reportString())

            assertThat(result).isInstanceOf(Result.Failure::class.java)
            assertThat(result.reportString()).isEqualToNormalizingWhitespace("""
            >> REQUEST.BODY[0].address
            Expected "123 Main St" to not equal "123 Main St"
            >> REQUEST.BODY[2].address
            Expected "123 Main St" to not equal "123 Main St"
            """.trimIndent())
        }
    }

    @Test
    fun `should be able to create dynamic asserts based on prefix value`() {
        val assert = AssertConditional(
            prefix = "REQUEST.BODY",
            conditionalAsserts = listOf(AssertComparison(prefix = "REQUEST.BODY", key = "name", lookupKey = "ENTITY.name", isEqualityCheck = true)),
            thenAsserts = listOf(AssertComparison(prefix = "REQUEST.BODY", key = "address", lookupKey = "ENTITY.address", isEqualityCheck = true)),
            elseAsserts = listOf(AssertComparison(prefix = "REQUEST.BODY", key = "address", lookupKey = "ENTITY.address", isEqualityCheck = false))
        )

        val jsonValue = JSONObjectValue(mapOf("name" to StringValue("Jane")))
        val arrayValue = JSONArrayValue(List(3) { jsonValue })

        val arrayBasedAsserts = assert.dynamicAsserts(arrayValue)
        assertThat(arrayBasedAsserts.size).isEqualTo(3)
        arrayBasedAsserts.forEachIndexed { index, it ->

            assertThat(it.conditionalAsserts.size).isEqualTo(1)
            assertThat(it.conditionalAsserts).allSatisfy {
                assertThat(it).isInstanceOf(AssertComparison::class.java)
                it as AssertComparison
                assertThat(it.prefix).isEqualTo("REQUEST.BODY[$index]")
                assertThat(it.key).isEqualTo("name")
                assertThat(it.lookupKey).isEqualTo("ENTITY.name")
                assertThat(it.isEqualityCheck).isTrue
            }

            assertThat(it.thenAsserts.size).isEqualTo(1)
            assertThat(it.thenAsserts).allSatisfy {
                assertThat(it).isInstanceOf(AssertComparison::class.java)
                it as AssertComparison
                assertThat(it.prefix).isEqualTo("REQUEST.BODY[$index]")
                assertThat(it.key).isEqualTo("address")
                assertThat(it.lookupKey).isEqualTo("ENTITY.address")
                assertThat(it.isEqualityCheck).isTrue
            }

            assertThat(it.elseAsserts.size).isEqualTo(1)
            assertThat(it.elseAsserts).allSatisfy {
                assertThat(it).isInstanceOf(AssertComparison::class.java)
                it as AssertComparison
                assertThat(it.prefix).isEqualTo("REQUEST.BODY[$index]")
                assertThat(it.key).isEqualTo("address")
                assertThat(it.lookupKey).isEqualTo("ENTITY.address")
                assertThat(it.isEqualityCheck).isFalse
            }
        }

        val jsonBasedAsserts = assert.dynamicAsserts(jsonValue)
        assertThat(jsonBasedAsserts.size).isEqualTo(1)
        assertThat(jsonBasedAsserts).allSatisfy {
            assertThat(it.conditionalAsserts).allSatisfy { assert ->

                assertThat(it.conditionalAsserts.size).isEqualTo(1)
                assertThat(assert).isInstanceOf(AssertComparison::class.java)
                assert as AssertComparison
                assertThat(assert.prefix).isEqualTo("REQUEST.BODY")
                assertThat(assert.key).isEqualTo("name")
                assertThat(assert.lookupKey).isEqualTo("ENTITY.name")
                assertThat(assert.isEqualityCheck).isTrue
            }

            assertThat(it.thenAsserts.size).isEqualTo(1)
            assertThat(it.thenAsserts).allSatisfy { assert ->
                assertThat(assert).isInstanceOf(AssertComparison::class.java)
                assert as AssertComparison
                assertThat(assert.prefix).isEqualTo("REQUEST.BODY")
                assertThat(assert.key).isEqualTo("address")
                assertThat(assert.lookupKey).isEqualTo("ENTITY.address")
                assertThat(assert.isEqualityCheck).isTrue
            }

            assertThat(it.elseAsserts.size).isEqualTo(1)
            assertThat(it.elseAsserts).allSatisfy { assert ->
                assertThat(assert).isInstanceOf(AssertComparison::class.java)
                assert as AssertComparison
                assertThat(assert.prefix).isEqualTo("REQUEST.BODY")
                assertThat(assert.key).isEqualTo("address")
                assertThat(assert.lookupKey).isEqualTo("ENTITY.address")
                assertThat(assert.isEqualityCheck).isFalse
            }
        }
    }

    @Test
    fun `should be able to parse conditions and then and else asserts`() {
        val assertSyntax = JSONObjectValue(mapOf(
            "\$conditions" to JSONObjectValue(mapOf(
                "name" to StringValue("\$eq(ENTITY.name)"),
            )),
            "\$then" to JSONObjectValue(mapOf(
                "address" to StringValue("\$eq(ENTITY.address)")
            )),
            "\$else" to JSONObjectValue(mapOf(
                "address" to StringValue("\$neq(ENTITY.address)")
            ))
        ))

        val assert = AssertConditional.parse("REQUEST.BODY", "\$if", assertSyntax, Resolver())

        assertThat(assert).isInstanceOf(AssertConditional::class.java)
        assert as AssertConditional
        assertThat(assert.prefix).isEqualTo("REQUEST.BODY")
        assertThat(assert.conditionalAsserts.size).isEqualTo(1)
        assertThat(assert.conditionalAsserts).allSatisfy {
            assertThat(it).isInstanceOf(AssertComparison::class.java)
            it as AssertComparison
            assertThat(it.prefix).isEqualTo("REQUEST.BODY")
            assertThat(it.key).isEqualTo("name")
            assertThat(it.lookupKey).isEqualTo("ENTITY.name")
            assertThat(it.isEqualityCheck).isTrue
        }

        assertThat(assert.thenAsserts.size).isEqualTo(1)
        assertThat(assert.thenAsserts).allSatisfy {
            assertThat(it).isInstanceOf(AssertComparison::class.java)
            it as AssertComparison
            assertThat(it.prefix).isEqualTo("REQUEST.BODY")
            assertThat(it.key).isEqualTo("address")
            assertThat(it.lookupKey).isEqualTo("ENTITY.address")
            assertThat(it.isEqualityCheck).isTrue
        }

        assertThat(assert.elseAsserts.size).isEqualTo(1)
        assertThat(assert.elseAsserts).allSatisfy {
            assertThat(it).isInstanceOf(AssertComparison::class.java)
            it as AssertComparison
            assertThat(it.prefix).isEqualTo("REQUEST.BODY")
            assertThat(it.key).isEqualTo("address")
            assertThat(it.lookupKey).isEqualTo("ENTITY.address")
            assertThat(it.isEqualityCheck).isFalse
        }
    }
}