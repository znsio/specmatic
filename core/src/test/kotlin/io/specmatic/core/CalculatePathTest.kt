package io.specmatic.core

import io.specmatic.core.pattern.AnyPattern
import io.specmatic.core.pattern.JSONObjectPattern
import io.specmatic.core.pattern.JSONArrayPattern
import io.specmatic.core.pattern.ListPattern
import io.specmatic.core.pattern.StringPattern
import io.specmatic.core.pattern.NumberPattern
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.JSONArrayValue
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

    @Test
    fun `calculatePath should find nested AnyPatterns`() {
        val nestedPattern = JSONObjectPattern(
            pattern = mapOf(
                "nestedData" to AnyPattern(listOf(StringPattern(), NumberPattern()))
            ),
            typeAlias = "NestedObject"
        )
        
        val pattern = JSONObjectPattern(
            pattern = mapOf(
                "id" to StringPattern(),
                "nested" to nestedPattern
            ),
            typeAlias = "MainObject"
        )

        val value = JSONObjectValue(mapOf(
            "id" to StringValue("123"),
            "nested" to JSONObjectValue(mapOf(
                "nestedData" to StringValue("some data")
            ))
        ))

        val paths = pattern.calculatePath(value)
        
        assertThat(paths).containsExactly("MainObject.nested.NestedObject.nestedData")
    }

    @Test
    fun `calculatePath should handle multiple AnyPatterns in same object`() {
        val pattern = JSONObjectPattern(
            pattern = mapOf(
                "data1" to AnyPattern(listOf(StringPattern(), NumberPattern())),
                "data2" to AnyPattern(listOf(StringPattern(), NumberPattern())),
                "regularField" to StringPattern()
            ),
            typeAlias = "MultiAnyObject"
        )

        val value = JSONObjectValue(mapOf(
            "data1" to StringValue("first"),
            "data2" to NumberValue(42),
            "regularField" to StringValue("regular")
        ))

        val paths = pattern.calculatePath(value)
        
        assertThat(paths).containsExactlyInAnyOrder("MultiAnyObject.data1", "MultiAnyObject.data2")
    }

    @Test
    fun `Feature calculatePath should iterate through scenarios and find paths`() {
        val requestPattern1 = HttpRequestPattern(
            body = JSONObjectPattern(
                pattern = mapOf(
                    "field1" to AnyPattern(listOf(StringPattern(), NumberPattern()))
                ),
                typeAlias = "Request1"
            )
        )
        val responsePattern1 = HttpResponsePattern(
            headersPattern = HttpHeadersPattern(),
            status = 200,
            body = StringPattern()
        )
        
        val scenario1 = Scenario(
            name = "scenario1",
            httpRequestPattern = requestPattern1,
            httpResponsePattern = responsePattern1
        )

        val requestPattern2 = HttpRequestPattern(
            body = JSONObjectPattern(
                pattern = mapOf(
                    "field2" to AnyPattern(listOf(StringPattern(), NumberPattern()))
                ),
                typeAlias = "Request2"
            )
        )
        val responsePattern2 = HttpResponsePattern(
            headersPattern = HttpHeadersPattern(),
            status = 200,
            body = StringPattern()
        )
        
        val scenario2 = Scenario(
            name = "scenario2",
            httpRequestPattern = requestPattern2,
            httpResponsePattern = responsePattern2
        )

        val feature = Feature(
            scenarios = listOf(scenario1, scenario2),
            name = "TestFeature"
        )

        val httpRequest = HttpRequest(
            method = "POST",
            path = "/test",
            body = JSONObjectValue(mapOf(
                "field1" to StringValue("value1"),
                "field2" to NumberValue(42)
            ))
        )

        val paths = feature.calculatePath(httpRequest)
        
        assertThat(paths).containsExactlyInAnyOrder("Request1.field1", "Request2.field2")
    }

    @Test
    fun `calculatePath should find AnyPatterns in arrays`() {
        val pattern = JSONObjectPattern(
            pattern = mapOf(
                "items" to JSONArrayPattern(
                    pattern = listOf(AnyPattern(listOf(StringPattern(), NumberPattern())))
                )
            ),
            typeAlias = "ArrayContainer"
        )

        val value = JSONObjectValue(mapOf(
            "items" to JSONArrayValue(listOf(
                StringValue("item1"),
                NumberValue(42),
                StringValue("item3")
            ))
        ))

        val paths = pattern.calculatePath(value)
        
        assertThat(paths).containsExactlyInAnyOrder(
            "ArrayContainer.items[0]",
            "ArrayContainer.items[1]", 
            "ArrayContainer.items[2]"
        )
    }

    @Test
    fun `calculatePath should find nested AnyPatterns in array objects`() {
        val arrayItemPattern = JSONObjectPattern(
            pattern = mapOf(
                "data" to AnyPattern(listOf(StringPattern(), NumberPattern()))
            ),
            typeAlias = "ArrayItem"
        )
        
        val pattern = JSONObjectPattern(
            pattern = mapOf(
                "items" to JSONArrayPattern(
                    pattern = listOf(arrayItemPattern)
                )
            ),
            typeAlias = "ArrayContainer"
        )

        val value = JSONObjectValue(mapOf(
            "items" to JSONArrayValue(listOf(
                JSONObjectValue(mapOf("data" to StringValue("first"))),
                JSONObjectValue(mapOf("data" to NumberValue(42)))
            ))
        ))

        val paths = pattern.calculatePath(value)
        
        assertThat(paths).containsExactlyInAnyOrder(
            "ArrayContainer.items[0].ArrayItem.data",
            "ArrayContainer.items[1].ArrayItem.data"
        )
    }
}