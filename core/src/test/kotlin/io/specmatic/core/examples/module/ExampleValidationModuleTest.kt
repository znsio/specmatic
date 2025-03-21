package io.specmatic.core.examples.module

import io.specmatic.core.*
import io.specmatic.core.pattern.*
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.NumberValue
import io.specmatic.core.value.StringValue
import io.specmatic.mock.ScenarioStub
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ExampleValidationModuleTest {
    private val exampleValidationModule = ExampleValidationModule()

    @Test
    fun `should be able to match on pattern tokens instead of literal values`() {
        val scenario = Scenario(
            ScenarioInfo(
            httpRequestPattern = HttpRequestPattern(
                method = "POST",
                httpPathPattern = buildHttpPathPattern("/add"),
                body = JSONObjectPattern(mapOf("first" to NumberPattern(), "second" to NumberPattern()))
            ),
            httpResponsePattern = HttpResponsePattern(
                status = 200,
                body = JSONObjectPattern(mapOf("result" to NumberPattern()))
            )
        )
        )
        val example = ScenarioStub(
            request = HttpRequest(
                method = "POST",
                path = "/add",
                body = JSONObjectValue(mapOf(
                    "first" to StringValue("(number)"),
                    "second" to NumberValue(10)
                ))
            ),
            response = HttpResponse(
                status = 200,
                body = JSONObjectValue(mapOf(
                    "result" to StringValue("(number)")
                ))
            )
        )

        val result = exampleValidationModule.validateExample(Feature(listOf(scenario), name= ""), example)
        assertThat(result.toResultIfAny()).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should complain when pattern token does not match the underlying pattern`() {
        val scenario = Scenario(
            ScenarioInfo(
            httpRequestPattern = HttpRequestPattern(
                method = "POST",
                httpPathPattern = buildHttpPathPattern("/add"),
                body = JSONObjectPattern(mapOf("first" to NumberPattern(), "second" to NumberPattern()))
            ),
            httpResponsePattern = HttpResponsePattern(
                status = 200,
                body = JSONObjectPattern(mapOf("result" to NumberPattern()))
            )
        )
        )
        val example = ScenarioStub(
            request = HttpRequest(
                method = "POST",
                path = "/add",
                body = JSONObjectValue(mapOf(
                    "first" to StringValue("(string)"),
                    "second" to NumberValue(10)
                ))
            ),
            response = HttpResponse(
                status = 200,
                body = JSONObjectValue(mapOf(
                    "result" to StringValue("(uuid)")
                ))
            )
        )

        val result = exampleValidationModule.validateExample(Feature(listOf(scenario), name= ""), example)
        assertThat(result.report()).isEqualToNormalizingWhitespace("""
        In scenario ""
        API: POST /add -> 200
        >> REQUEST.BODY.first
        Specification expected number but example contained string
        >> RESPONSE.BODY.result
        Specification expected number but example contained uuid
        """.trimIndent())
    }

    @Test
    fun `should match pattern tokens on schema examples`() {
        val pattern = JSONObjectPattern(mapOf("first" to NumberPattern(), "second" to NumberPattern()))
        val example = JSONObjectValue(mapOf("first" to StringValue("(number)"), "second" to NumberValue(10)))

        val scenario = Scenario(ScenarioInfo(patterns = mapOf("(Test)" to pattern)))
        val feature = Feature(listOf(scenario), name= "")

        val result = feature.matchResultSchemaFlagBased(null, "Test", example, DefaultMismatchMessages)
        assertThat(result).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should result in a failure on pattern token mismatch on schema examples`() {
        val pattern = JSONObjectPattern(mapOf("first" to NumberPattern(), "second" to NumberPattern()))
        val example = JSONObjectValue(mapOf("first" to StringValue("(string)"), "second" to NumberValue(10)))

        val scenario = Scenario(ScenarioInfo(patterns = mapOf("(Test)" to pattern)))
        val feature = Feature(listOf(scenario), name= "")

        val result = feature.matchResultSchemaFlagBased(null, "Test", example, DefaultMismatchMessages)
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat(result.reportString()).isEqualToNormalizingWhitespace("""
        >> first
        Expected number, actual was string
        """.trimIndent())
    }

    @Test
    fun `should provide meaningful error message when 2xx example has path mutation`() {
        val scenario = Scenario(
            ScenarioInfo(
                httpRequestPattern = HttpRequestPattern(method = "GET", httpPathPattern = buildHttpPathPattern("/test/(id:number)/name/(name:string)")),
                httpResponsePattern = HttpResponsePattern(status = 200)
            )
        )
        val feature = Feature(listOf(scenario), name = "")
        val example = ScenarioStub(request = HttpRequest(method = "GET", path = "/test/abc/name/123"), response = HttpResponse.OK)
        val result = exampleValidationModule.validateExample(feature, example).toResultIfAny()

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat(result.reportString()).isEqualToNormalizingWhitespace("""
        In scenario ""
        API: GET /test/(id:number)/name/(name:string) -> 200
        >> REQUEST.PATH.id
        Specification expected number but example contained "abc"
        """.trimIndent())
    }

    @Test
    fun `should not complain when path mutation happens on 4xx example`() {
        val scenario = Scenario(
            ScenarioInfo(
                httpRequestPattern = HttpRequestPattern(method = "GET", httpPathPattern = buildHttpPathPattern("/test/(id:number)/name/(name:string)")),
                httpResponsePattern = HttpResponsePattern(status = 400)
            )
        )
        val feature = Feature(listOf(scenario), name = "")
        val example = ScenarioStub(request = HttpRequest(method = "GET", path = "/test/abc/name/123"), response = HttpResponse(status = 400))
        val result = exampleValidationModule.validateExample(feature, example)

        assertThat(result.toResultIfAny()).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should be able to validate partial example`(@TempDir tempDir: File) {
        val scenario = Scenario(
            ScenarioInfo(
                httpRequestPattern = HttpRequestPattern(
                    method = "POST",
                    httpPathPattern = buildHttpPathPattern("/test"),
                    body = JSONObjectPattern(mapOf("name" to StringPattern(), "age" to NumberPattern()))
                ),
                httpResponsePattern = HttpResponsePattern(
                    status = 201,
                    body = JSONObjectPattern(mapOf("id" to NumberPattern(), "name" to StringPattern(), "age" to NumberPattern()))
                )
            )
        )
        val feature = Feature(listOf(scenario), name = "")

        val example = ScenarioStub(
            request = HttpRequest("POST", "/test", body = parsedJSONObject("""{"name": "John"}""")),
            response = HttpResponse(status = 201, body = JSONObjectValue(emptyMap()))
        )
        val exampleFile = example.toPartialExample(tempDir)
        val result = exampleValidationModule.validateExample(feature, exampleFile)

        assertThat(result).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should return failure when partial example has invalid value`(@TempDir tempDir: File) {
        val scenario = Scenario(
            ScenarioInfo(
                httpRequestPattern = HttpRequestPattern(
                    method = "POST",
                    httpPathPattern = buildHttpPathPattern("/test"),
                    body = JSONObjectPattern(mapOf("name" to StringPattern(), "age" to NumberPattern()))
                ),
                httpResponsePattern = HttpResponsePattern(
                    status = 201,
                    body = JSONObjectPattern(mapOf("id" to NumberPattern(), "name" to StringPattern(), "age" to NumberPattern()))
                )
            )
        )
        val feature = Feature(listOf(scenario), name = "")

        val example = ScenarioStub(
            request = HttpRequest("POST", "/test", body = parsedJSONObject("""{"name": 123}""")),
            response = HttpResponse(status = 201, body = parsedJSONObject("""{"id": "abc"}"""))
        )
        val exampleFile = example.toPartialExample(tempDir)
        val result = exampleValidationModule.validateExample(feature, exampleFile)

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat(result.reportString()).isEqualToNormalizingWhitespace("""
        >> REQUEST.BODY.name
        Specification expected string but example contained 123 (number)
        >> RESPONSE.BODY.id
        Specification expected number but example contained "abc"
        """.trimIndent())
    }

    @Test
    fun `should be able to validate partial example with pattern tokens`(@TempDir tempDir: File) {
        val scenario = Scenario(
            ScenarioInfo(
                httpRequestPattern = HttpRequestPattern(
                    method = "POST",
                    httpPathPattern = buildHttpPathPattern("/test"),
                    body = JSONObjectPattern(mapOf("name" to StringPattern(), "age" to NumberPattern()))
                ),
                httpResponsePattern = HttpResponsePattern(
                    status = 201,
                    body = JSONObjectPattern(mapOf("id" to NumberPattern(), "name" to StringPattern(), "age" to NumberPattern()))
                )
            )
        )
        val feature = Feature(listOf(scenario), name = "")

        val example = ScenarioStub(
            request = HttpRequest("POST", "/test", body = parsedJSONObject("""{"name": "(number)", "age": "(number)"}""")),
            response = HttpResponse(status = 201, body = parsedJSONObject("""{"id": "(string)"}"""))
        )
        val exampleFile = example.toPartialExample(tempDir)
        val result = exampleValidationModule.validateExample(feature, exampleFile)

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat(result.reportString()).isEqualToNormalizingWhitespace("""
        >> REQUEST.BODY.name
        Specification expected string but example contained number
        >> RESPONSE.BODY.id
        Specification expected number but example contained string
        """.trimIndent())
    }

    @Test
    fun `should return failure when values are missing on non-partial example`(@TempDir tempDir: File) {
        val scenario = Scenario(
            ScenarioInfo(
                httpRequestPattern = HttpRequestPattern(
                    method = "POST",
                    httpPathPattern = buildHttpPathPattern("/test"),
                    body = JSONObjectPattern(mapOf("name" to StringPattern(), "age" to NumberPattern()))
                ),
                httpResponsePattern = HttpResponsePattern(
                    status = 201,
                    body = JSONObjectPattern(mapOf("id" to NumberPattern(), "name" to StringPattern(), "age" to NumberPattern()))
                )
            )
        )
        val feature = Feature(listOf(scenario), name = "")

        val example = ScenarioStub(
            request = HttpRequest("POST", "/test", body = parsedJSONObject("""{"name": "John"}""")),
            response = HttpResponse(status = 201, body = JSONObjectValue(emptyMap()))
        )
        val exampleFile = tempDir.resolve("example.json")
        exampleFile.writeText(example.toJSON().toStringLiteral())
        val result = exampleValidationModule.validateExample(feature, exampleFile)

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat(result.reportString()).isEqualToNormalizingWhitespace("""
        >> REQUEST.BODY.age
        Key age in the specification is missing from the example
        >> RESPONSE.BODY.id
        Key id in the specification is missing from the example
        >> RESPONSE.BODY.name
        Key name in the specification is missing from the example
        >> RESPONSE.BODY.age
        Key age in the specification is missing from the example
        """.trimIndent())
    }

    @Test
    fun `should complain when response does not adhere to attribute selection`(@TempDir tempDir: File) {
        val scenario = Scenario(
            ScenarioInfo(
                httpRequestPattern = HttpRequestPattern(
                    method = "GET",
                    httpPathPattern = buildHttpPathPattern("/test"),
                    httpQueryParamPattern = HttpQueryParamPattern(mapOf("columns" to QueryParameterScalarPattern(StringPattern())))
                ),
                httpResponsePattern = HttpResponsePattern(
                    status = 201,
                    body = ListPattern(JSONObjectPattern(mapOf("id" to NumberPattern(), "name" to StringPattern(), "age" to NumberPattern())))
                )
            )
        ).copy(attributeSelectionPattern = AttributeSelectionPattern(defaultFields = listOf("id"), queryParamKey = "columns"))
        val feature = Feature(listOf(scenario), name = "")

        val example = ScenarioStub(
            request = HttpRequest("GET", "/test", queryParams = QueryParameters(mapOf("columns" to "name"))),
            response = HttpResponse(status = 201, body = parsedJSONArray("""[ {"age": 10, "extra": "value"} ]"""))
        )
        val exampleFile = example.toExample(tempDir)
        val result = exampleValidationModule.validateExample(feature, exampleFile)

        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat(result.reportString()).isEqualToNormalizingWhitespace("""
        >> RESPONSE.BODY[0].id
        Expected key named "id" was missing
        >> RESPONSE.BODY[0].name
        Expected key named "name" was missing
        >> RESPONSE.BODY[0].age
        Key named "age" was unexpected
        >> RESPONSE.BODY[0].extra
        Key named "extra" was unexpected
        """.trimIndent())
    }

    @Test
    fun `should not complain when mandatory keys are missing because they're not attribute selected`(@TempDir tempDir: File) {
        val scenario = Scenario(
            ScenarioInfo(
                httpRequestPattern = HttpRequestPattern(
                    method = "GET",
                    httpPathPattern = buildHttpPathPattern("/test"),
                    httpQueryParamPattern = HttpQueryParamPattern(mapOf("columns" to QueryParameterScalarPattern(StringPattern())))
                ),
                httpResponsePattern = HttpResponsePattern(
                    status = 201,
                    body = ListPattern(JSONObjectPattern(mapOf("id" to NumberPattern(), "name" to StringPattern(), "age" to NumberPattern())))
                )
            )
        ).copy(attributeSelectionPattern = AttributeSelectionPattern(defaultFields = listOf("id"), queryParamKey = "columns"))
        val feature = Feature(listOf(scenario), name = "")

        val example = ScenarioStub(
            request = HttpRequest("GET", "/test", queryParams = QueryParameters(mapOf("columns" to "name"))),
            response = HttpResponse(status = 201, body = parsedJSONArray("""[ {"id": 10, "name": "JohnDoe"} ]"""))
        )
        val exampleFile = example.toExample(tempDir)
        val result = exampleValidationModule.validateExample(feature, exampleFile)

        assertThat(result).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `attribute selection should work with partial example`(@TempDir tempDir: File) {
        val scenario = Scenario(
            ScenarioInfo(
                httpRequestPattern = HttpRequestPattern(
                    method = "GET",
                    httpPathPattern = buildHttpPathPattern("/test"),
                    httpQueryParamPattern = HttpQueryParamPattern(mapOf("columns" to QueryParameterScalarPattern(StringPattern())))
                ),
                httpResponsePattern = HttpResponsePattern(
                    status = 201,
                    body = ListPattern(JSONObjectPattern(mapOf("id" to NumberPattern(), "name" to StringPattern(), "age" to NumberPattern())))
                )
            )
        ).copy(attributeSelectionPattern = AttributeSelectionPattern(defaultFields = listOf("id"), queryParamKey = "columns"))
        val feature = Feature(listOf(scenario), name = "")

        val invalidExample = ScenarioStub(
            request = HttpRequest("GET", "/test", queryParams = QueryParameters(mapOf("columns" to "name"))),
            response = HttpResponse(status = 201, body = parsedJSONArray("""[ {"age": 10, "extra": "value"} ]"""))
        )
        val validExample = ScenarioStub(
            request = HttpRequest("GET", "/test", queryParams = QueryParameters(mapOf("columns" to "name"))),
            response = HttpResponse(status = 201, body = parsedJSONArray("""[ {"id": 10, "name": "JohnDoe"} ]"""))
        )

        val partialInvalidExample = invalidExample.toPartialExample(tempDir)
        val partialInvalidExampleResult = exampleValidationModule.validateExample(feature, partialInvalidExample)

        val partialValidExample = validExample.toPartialExample(tempDir)
        val partialValidExampleResult = exampleValidationModule.validateExample(feature, partialValidExample)

        assertThat(partialValidExampleResult).isInstanceOf(Result.Success::class.java)
        assertThat(partialInvalidExampleResult).isInstanceOf(Result.Failure::class.java)
        assertThat(partialInvalidExampleResult.reportString()).isEqualToNormalizingWhitespace("""
        >> RESPONSE.BODY[0].id
        Expected key named "id" was missing
        >> RESPONSE.BODY[0].name
        Expected key named "name" was missing
        >> RESPONSE.BODY[0].age
        Key named "age" was unexpected
        >> RESPONSE.BODY[0].extra
        Key named "extra" was unexpected
        """.trimIndent())
    }

    private fun ScenarioStub.toPartialExample(tempDir: File): File {
        val example = JSONObjectValue(mapOf("partial" to this.toJSON()))
        val exampleFile = tempDir.resolve("example.json")
        exampleFile.writeText(example.toStringLiteral())
        return exampleFile
    }

    private fun ScenarioStub.toExample(tempDir: File): File {
        val example = this.toJSON()
        val exampleFile = tempDir.resolve("example.json")
        exampleFile.writeText(example.toStringLiteral())
        return exampleFile
    }
}