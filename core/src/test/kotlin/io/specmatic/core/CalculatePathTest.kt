package io.specmatic.core

import io.specmatic.core.pattern.AnyPattern
import io.specmatic.core.pattern.JSONObjectPattern
import io.specmatic.core.pattern.JSONArrayPattern
import io.specmatic.core.pattern.ListPattern
import io.specmatic.core.pattern.StringPattern
import io.specmatic.core.pattern.NumberPattern
import io.specmatic.core.pattern.DeferredPattern
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

        val paths = pattern.calculatePath(value, Resolver())
        
        assertThat(paths).containsExactly("{User}.data{string}")
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

        val paths = pattern.calculatePath(value, Resolver())
        
        assertThat(paths).containsExactly("value{number}")
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
        
        // Since we have an AnyPattern at the top level that matches a scalar type,
        // it should return the scalar type name
        assertThat(paths).containsExactly("string")
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

        val paths = pattern.calculatePath(value, Resolver())
        
        assertThat(paths).isEmpty()
    }

    @Test
    fun `calculatePath should handle non-JSON object value`() {
        val pattern = JSONObjectPattern(
            pattern = mapOf(
                "data" to AnyPattern(listOf(StringPattern(), NumberPattern()))
            )
        )

        val paths = pattern.calculatePath(StringValue("not an object"), Resolver())
        
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

        val paths = pattern.calculatePath(value, Resolver())
        
        assertThat(paths).containsExactly("{MainObject}.nested.{NestedObject}.nestedData{string}")
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

        val paths = pattern.calculatePath(value, Resolver())
        
        assertThat(paths).containsExactlyInAnyOrder("{MultiAnyObject}.data1{string}", "{MultiAnyObject}.data2{number}")
    }

    @Test
    fun `Feature calculatePath should find paths from first matching scenario`() {
        val requestPattern1 = HttpRequestPattern(
            method = "POST",
            httpPathPattern = buildHttpPathPattern("/test"),
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
            method = "POST",
            httpPathPattern = buildHttpPathPattern("/test"),
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

        val paths = feature.calculatePath(httpRequest, 200)
        
        assertThat(paths).containsExactly("{Request1}.field1{string}")
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

        val paths = pattern.calculatePath(value, Resolver())
        
        assertThat(paths).containsExactlyInAnyOrder(
            "{ArrayContainer}.items[0]{string}",
            "{ArrayContainer}.items[1]{number}", 
            "{ArrayContainer}.items[2]{string}"
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

        val paths = pattern.calculatePath(value, Resolver())
        
        assertThat(paths).containsExactlyInAnyOrder(
            "{ArrayContainer}.items[0].{ArrayItem}.data{string}",
            "{ArrayContainer}.items[1].{ArrayItem}.data{number}"
        )
    }

    @Test
    fun `calculatePath should handle ListPattern with AnyPattern`() {
        val pattern = JSONObjectPattern(
            pattern = mapOf(
                "items" to ListPattern(
                    pattern = AnyPattern(listOf(StringPattern(), NumberPattern()))
                )
            ),
            typeAlias = "ListContainer"
        )

        val value = JSONObjectValue(mapOf(
            "items" to JSONArrayValue(listOf(
                StringValue("item1"),
                NumberValue(42)
            ))
        ))

        val paths = pattern.calculatePath(value, Resolver())
        
        assertThat(paths).containsExactlyInAnyOrder(
            "{ListContainer}.items[0]{string}",
            "{ListContainer}.items[1]{number}"
        )
    }

    @Test
    fun `calculatePath should handle empty arrays`() {
        val pattern = JSONObjectPattern(
            pattern = mapOf(
                "items" to JSONArrayPattern(
                    pattern = listOf(AnyPattern(listOf(StringPattern(), NumberPattern())))
                )
            ),
            typeAlias = "EmptyArrayContainer"
        )

        val value = JSONObjectValue(mapOf(
            "items" to JSONArrayValue(emptyList())
        ))

        val paths = pattern.calculatePath(value, Resolver())
        
        assertThat(paths).isEmpty()
    }

    @Test
    fun `calculatePath should handle missing optional keys`() {
        val pattern = JSONObjectPattern(
            pattern = mapOf(
                "requiredField" to StringPattern(),
                "optionalField?" to AnyPattern(listOf(StringPattern(), NumberPattern()))
            ),
            typeAlias = "OptionalFieldObject"
        )

        val value = JSONObjectValue(mapOf(
            "requiredField" to StringValue("value")
            // optionalField is missing
        ))

        val paths = pattern.calculatePath(value, Resolver())
        
        assertThat(paths).isEmpty()
    }
    
    @Test
    fun `calculatePath should handle AnyPattern at top level with typeAlias`() {
        val patterns = mapOf(
            "(Address)" to JSONObjectPattern(
                pattern = mapOf("street" to StringPattern(), "locality" to StringPattern())
            ),
            "(AddressRef)" to JSONObjectPattern(
                pattern = mapOf("address_id" to NumberPattern())
            )
        )
        
        val scenario = Scenario(
            name = "test",
            httpRequestPattern = HttpRequestPattern(
                body = AnyPattern(
                    pattern = listOf(
                        DeferredPattern(pattern = "(Address)"),
                        DeferredPattern(pattern = "(AddressRef)")
                    ),
                    typeAlias = "(AddressOrRef)"
                )
            ),
            httpResponsePattern = HttpResponsePattern(
                headersPattern = HttpHeadersPattern(),
                status = 200,
                body = StringPattern()
            ),
            patterns = patterns
        )

        val httpRequest = HttpRequest(
            method = "POST",
            path = "/test",
            body = JSONObjectValue(mapOf("address_id" to NumberValue(123)))
        )

        val paths = scenario.calculatePath(httpRequest)
        
        assertThat(paths).containsExactly("AddressRef")
    }
    
    @Test
    fun `calculatePath should handle AnyPattern at top level without typeAlias`() {
        val patterns = mapOf(
            "(Address)" to JSONObjectPattern(
                pattern = mapOf("street" to StringPattern(), "locality" to StringPattern())
            ),
            "(AddressRef)" to JSONObjectPattern(
                pattern = mapOf("address_id" to NumberPattern())
            )
        )
        
        val scenario = Scenario(
            name = "test",
            httpRequestPattern = HttpRequestPattern(
                body = AnyPattern(
                    pattern = listOf(
                        DeferredPattern(pattern = "(Address)"),
                        DeferredPattern(pattern = "(AddressRef)")
                    )
                    // No typeAlias for the AnyPattern itself
                )
            ),
            httpResponsePattern = HttpResponsePattern(
                headersPattern = HttpHeadersPattern(),
                status = 200,
                body = StringPattern()
            ),
            patterns = patterns
        )

        val httpRequest = HttpRequest(
            method = "POST",
            path = "/test",
            body = JSONObjectValue(mapOf("address_id" to NumberValue(123)))
        )

        val paths = scenario.calculatePath(httpRequest)
        
        assertThat(paths).containsExactly("AddressRef")
    }
    
    @Test
    fun `calculatePath should handle AnyPattern where one option has no typeAlias`() {
        val patterns = mapOf(
            "(Address)" to JSONObjectPattern(
                pattern = mapOf("street" to StringPattern(), "locality" to StringPattern())
            )
        )
        
        val scenario = Scenario(
            name = "test",
            httpRequestPattern = HttpRequestPattern(
                body = AnyPattern(
                    pattern = listOf(
                        DeferredPattern(pattern = "(Address)"),
                        JSONObjectPattern(pattern = mapOf("address_id" to NumberPattern()))
                    )
                )
            ),
            httpResponsePattern = HttpResponsePattern(
                headersPattern = HttpHeadersPattern(),
                status = 200,
                body = StringPattern()
            ),
            patterns = patterns
        )

        val httpRequest = HttpRequest(
            method = "POST",
            path = "/test",
            body = JSONObjectValue(mapOf("address_id" to NumberValue(123)))
        )

        val paths = scenario.calculatePath(httpRequest)
        
        assertThat(paths).containsExactly("{[1]}")
    }
    
    @Test
    fun `calculatePath should handle array of AnyPattern objects`() {
        val patterns = mapOf(
            "(Address)" to JSONObjectPattern(
                pattern = mapOf("street" to StringPattern(), "locality" to StringPattern())
            ),
            "(AddressRef)" to JSONObjectPattern(
                pattern = mapOf("address_id" to NumberPattern())
            )
        )
        
        val scenario = Scenario(
            name = "test",
            httpRequestPattern = HttpRequestPattern(
                body = JSONObjectPattern(
                    pattern = mapOf(
                        "name" to StringPattern(),
                        "addresses" to ListPattern(
                            pattern = AnyPattern(
                                pattern = listOf(
                                    DeferredPattern(pattern = "(Address)"),
                                    DeferredPattern(pattern = "(AddressRef)")
                                )
                            )
                        )
                    ),
                    typeAlias = "Person"
                )
            ),
            httpResponsePattern = HttpResponsePattern(
                headersPattern = HttpHeadersPattern(),
                status = 200,
                body = StringPattern()
            ),
            patterns = patterns
        )

        val httpRequest = HttpRequest(
            method = "POST",
            path = "/test",
            body = JSONObjectValue(mapOf(
                "name" to StringValue("Jack"),
                "addresses" to JSONArrayValue(listOf(
                    JSONObjectValue(mapOf("address_id" to NumberValue(123))),
                    JSONObjectValue(mapOf("street" to StringValue("Baker Street"), "locality" to StringValue("London")))
                ))
            ))
        )

        val paths = scenario.calculatePath(httpRequest)
        
        assertThat(paths).containsExactlyInAnyOrder("{Person}.addresses[0]{AddressRef}", "{Person}.addresses[1]{Address}")
    }
    
    @Test
    fun `calculatePath should handle top-level array of AnyPatterns`() {
        val patterns = mapOf(
            "(Address)" to JSONObjectPattern(
                pattern = mapOf("street" to StringPattern(), "locality" to StringPattern())
            ),
            "(AddressRef)" to JSONObjectPattern(
                pattern = mapOf("address_id" to NumberPattern())
            )
        )
        
        val scenario = Scenario(
            name = "test",
            httpRequestPattern = HttpRequestPattern(
                body = ListPattern(
                    pattern = AnyPattern(
                        pattern = listOf(
                            DeferredPattern(pattern = "(Address)"),
                            DeferredPattern(pattern = "(AddressRef)")
                        )
                    ),
                    typeAlias = "(AddressList)"
                )
            ),
            httpResponsePattern = HttpResponsePattern(
                headersPattern = HttpHeadersPattern(),
                status = 200,
                body = StringPattern()
            ),
            patterns = patterns
        )

        val httpRequest = HttpRequest(
            method = "POST",
            path = "/test",
            body = JSONArrayValue(listOf(
                JSONObjectValue(mapOf("address_id" to NumberValue(123))),
                JSONObjectValue(mapOf("street" to StringValue("Baker Street"), "locality" to StringValue("London")))
            ))
        )

        val paths = scenario.calculatePath(httpRequest)
        
        assertThat(paths).containsExactlyInAnyOrder("[0]{AddressRef}", "[1]{Address}")
    }
    
    @Test
    fun `calculatePath should handle array of AnyPattern objects in ListPattern`() {
        // Pattern: JSONObjectPattern with ListPattern containing AnyPattern
        
        val patterns = mapOf(
            "(Address)" to JSONObjectPattern(
                pattern = mapOf(
                    "street" to StringPattern(),
                    "locality" to StringPattern()
                )
            ),
            "(AddressRef)" to JSONObjectPattern(
                pattern = mapOf(
                    "address_id" to NumberPattern()
                )
            )
        )
        
        val scenario = Scenario(
            name = "test",
            httpRequestPattern = HttpRequestPattern(
                body = JSONObjectPattern(
                    pattern = mapOf(
                        "name" to StringPattern(),
                        "addresses" to ListPattern(
                            pattern = AnyPattern(
                                pattern = listOf(
                                    DeferredPattern("(Address)"),
                                    DeferredPattern("(AddressRef)")
                                )
                            )
                        )
                    ),
                    typeAlias = "Person"
                )
            ),
            httpResponsePattern = HttpResponsePattern(
                headersPattern = HttpHeadersPattern(),
                status = 200,
                body = StringPattern()
            ),
            patterns = patterns
        )
        
        val httpRequest = HttpRequest(
            method = "POST",
            path = "/test",
            body = JSONObjectValue(mapOf(
                "name" to StringValue("Jack"),
                "addresses" to JSONArrayValue(listOf(
                    JSONObjectValue(mapOf("address_id" to NumberValue(123))),
                    JSONObjectValue(mapOf("street" to StringValue("Baker Street"), "locality" to StringValue("London")))
                ))
            ))
        )
        
        val paths = scenario.calculatePath(httpRequest)
        
        assertThat(paths).containsExactlyInAnyOrder("{Person}.addresses[0]{AddressRef}", "{Person}.addresses[1]{Address}")
    }
    
    @Test
    fun `calculatePath should handle multiple paths from different AnyPattern fields`() {
        // Pattern: JSONObjectPattern with multiple fields that resolve to AnyPatterns
        
        val patterns = mapOf(
            "(Address)" to JSONObjectPattern(
                pattern = mapOf(
                    "street" to StringPattern(),
                    "locality" to StringPattern()
                )
            ),
            "(AddressRef)" to JSONObjectPattern(
                pattern = mapOf(
                    "address_id" to NumberPattern()
                )
            ),
            "(AddressOrRef)" to AnyPattern(
                pattern = listOf(
                    DeferredPattern("(Address)"),
                    DeferredPattern("(AddressRef)")
                )
            )
        )
        
        val scenario = Scenario(
            name = "test",
            httpRequestPattern = HttpRequestPattern(
                body = JSONObjectPattern(
                    pattern = mapOf(
                        "name" to StringPattern(),
                        "officeAddress" to DeferredPattern("(AddressOrRef)"),
                        "homeAddress" to DeferredPattern("(AddressOrRef)")
                    ),
                    typeAlias = "Person"
                )
            ),
            httpResponsePattern = HttpResponsePattern(
                headersPattern = HttpHeadersPattern(),
                status = 200,
                body = StringPattern()
            ),
            patterns = patterns
        )
        
        val httpRequest = HttpRequest(
            method = "POST",
            path = "/test",
            body = JSONObjectValue(mapOf(
                "name" to StringValue("Jack"),
                "officeAddress" to JSONObjectValue(mapOf("address_id" to NumberValue(123))),
                "homeAddress" to JSONObjectValue(mapOf("street" to StringValue("Baker Street"), "locality" to StringValue("London")))
            ))
        )
        
        val paths = scenario.calculatePath(httpRequest)
        
        assertThat(paths).containsExactlyInAnyOrder("{Person}.officeAddress{AddressRef}", "{Person}.homeAddress{Address}")
    }
}