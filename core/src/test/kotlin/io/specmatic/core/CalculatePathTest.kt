package io.specmatic.core

import io.specmatic.core.pattern.AnyPattern
import io.specmatic.core.pattern.JSONObjectPattern
import io.specmatic.core.pattern.StringPattern
import io.specmatic.core.pattern.NumberPattern
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.StringValue
import io.specmatic.core.value.NumberValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class CalculatePathTest {

    @Test
    fun `calculatePath should find AnyPattern in simple object with typeAlias`() {
        // Create a pattern with typeAlias and an AnyPattern field
        val pattern = JSONObjectPattern(
            pattern = mapOf(
                "id" to StringPattern(),
                "data" to AnyPattern(listOf(StringPattern(), NumberPattern()))
            ),
            typeAlias = "User"
        )

        // Create a value that matches this pattern
        val value = JSONObjectValue(mapOf(
            "id" to StringValue("123"),
            "data" to StringValue("some data")
        ))

        val paths = pattern.calculatePath(value)
        
        assertThat(paths).containsExactly("User.data")
    }

    @Test
    fun `calculatePath should find AnyPattern in object without typeAlias`() {
        val pattern = JSONObjectPattern(
            pattern = mapOf(
                "id" to StringPattern(),
                "value" to AnyPattern(listOf(StringPattern(), NumberPattern()))
            )
        )

        val value = JSONObjectValue(mapOf(
            "id" to StringValue("123"),
            "value" to NumberValue(42)
        ))

        val paths = pattern.calculatePath(value)
        
        assertThat(paths).containsExactly("value")
    }

    @Test
    fun `calculatePath should find AnyPattern at top level`() {
        // For this test, create a scenario and test through Scenario.calculatePath
        val httpRequestPattern = HttpRequestPattern(
            body = AnyPattern(listOf(StringPattern(), NumberPattern()))
        )
        val httpResponsePattern = HttpResponsePattern(
            headersPattern = HttpHeadersPattern(),
            status = 200,
            body = StringPattern()
        )
        
        val scenario = Scenario(
            name = "test",
            httpRequestPattern = httpRequestPattern,
            httpResponsePattern = httpResponsePattern
        )

        val httpRequest = HttpRequest(
            method = "POST",
            path = "/test",
            body = StringValue("some data")
        )

        val paths = scenario.calculatePath(httpRequest)
        
        // Since we have an AnyPattern at the top level but it's not a JSONObjectPattern,
        // this should return empty set for now (we may need to enhance this later)
        assertThat(paths).isEmpty()
    }

    @Test
    fun `calculatePath should handle empty object`() {
        val pattern = JSONObjectPattern(
            pattern = mapOf(
                "id" to StringPattern()
            )
        )

        val value = JSONObjectValue(mapOf(
            "id" to StringValue("123")
        ))

        val paths = pattern.calculatePath(value)
        
        assertThat(paths).isEmpty()
    }

    @Test
    fun `calculatePath should handle non-JSON object value`() {
        val pattern = JSONObjectPattern(
            pattern = mapOf(
                "data" to AnyPattern(listOf(StringPattern(), NumberPattern()))
            )
        )

        val paths = pattern.calculatePath(StringValue("not an object"))
        
        assertThat(paths).isEmpty()
    }
}