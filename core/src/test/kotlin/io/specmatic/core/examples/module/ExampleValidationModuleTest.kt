package io.specmatic.core.examples.module

import io.specmatic.core.DefaultMismatchMessages
import io.specmatic.core.Feature
import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpRequestPattern
import io.specmatic.core.HttpResponse
import io.specmatic.core.HttpResponsePattern
import io.specmatic.core.Result
import io.specmatic.core.Scenario
import io.specmatic.core.ScenarioInfo
import io.specmatic.core.buildHttpPathPattern
import io.specmatic.core.pattern.JSONObjectPattern
import io.specmatic.core.pattern.NumberPattern
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.NumberValue
import io.specmatic.core.value.StringValue
import io.specmatic.mock.ScenarioStub
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

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
}