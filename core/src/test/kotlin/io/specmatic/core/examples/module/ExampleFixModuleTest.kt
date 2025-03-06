package io.specmatic.core.examples.module

import io.specmatic.core.Feature
import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpRequestPattern
import io.specmatic.core.HttpResponse
import io.specmatic.core.HttpResponsePattern
import io.specmatic.core.Scenario
import io.specmatic.core.ScenarioInfo
import io.specmatic.core.buildHttpPathPattern
import io.specmatic.core.examples.server.FixExampleStatus
import io.specmatic.core.examples.server.SchemaExample
import io.specmatic.core.pattern.BooleanPattern
import io.specmatic.core.pattern.DatePattern
import io.specmatic.core.pattern.JSONObjectPattern
import io.specmatic.core.pattern.NumberPattern
import io.specmatic.core.pattern.StringPattern
import io.specmatic.core.value.BooleanValue
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.NumberValue
import io.specmatic.core.value.StringValue
import io.specmatic.mock.ScenarioStub
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ExampleFixModuleTest {
    private val exampleFixModule = ExampleFixModule(ExampleValidationModule())

    @Test
    fun `should be able to fix invalid examples while retaining valid pattern tokens`(@TempDir tempDir: File) {
        val scenario = Scenario(
            ScenarioInfo(
            httpRequestPattern = HttpRequestPattern(
                method = "POST",
                httpPathPattern = buildHttpPathPattern("/"),
                body = JSONObjectPattern(mapOf(
                    "string" to StringPattern(), "number" to NumberPattern(),
                    "boolean" to BooleanPattern(), "date" to DatePattern
                ))
            ),
            httpResponsePattern = HttpResponsePattern(status = 200)
        )
        )
        val example = ScenarioStub(
            request = HttpRequest(
                method = "POST", path = "/",
                body = JSONObjectValue(mapOf(
                    "string" to StringValue("OK"),
                    "number" to StringValue("TODO"),
                    "boolean" to StringValue("(string)"),
                    "date" to StringValue("(date)")
                ))
            ),
            response = HttpResponse.OK
        )

        val feature = Feature(listOf(scenario), name= "")
        val exampleFile = File(tempDir, "example.json").apply { writeText(example.toJSON().toStringLiteral()) }
        val result = exampleFixModule.fixExample(feature, exampleFile)
        val fixedExampleReqBody = ScenarioStub.readFromFile(exampleFile).request.body as JSONObjectValue

        assertThat(result.status).isEqualTo(FixExampleStatus.SUCCEDED)
        assertThat(fixedExampleReqBody.findFirstChildByName("string")).isEqualTo(StringValue("OK"))
        assertThat(fixedExampleReqBody.findFirstChildByName("number")).isInstanceOf(NumberValue::class.java)
        assertThat(fixedExampleReqBody.findFirstChildByName("boolean")).isInstanceOf(BooleanValue::class.java)
        assertThat(fixedExampleReqBody.findFirstChildByName("date")).isEqualTo(StringValue("(date)"))
    }

    @Test
    fun `should be able to fix invalid schema example while retaining valid pattern tokens`(@TempDir tempDir: File) {
        val pattern = JSONObjectPattern(mapOf(
            "string" to StringPattern(), "number" to NumberPattern(),
            "boolean" to BooleanPattern(), "date" to DatePattern
        ), typeAlias = "(Test)")
        val example = JSONObjectValue(mapOf(
            "string" to StringValue("OK"), "number" to StringValue("TODO"),
            "boolean" to StringValue("(string)"), "date" to StringValue("(date)")
        ))

        val feature = Feature(listOf(Scenario(ScenarioInfo(patterns = mapOf("(Test)" to pattern)))), name= "")
        val exampleFile = File(tempDir, "resource.Test.example.json").apply { writeText(example.toStringLiteral()) }
        val result = exampleFixModule.fixExample(feature, exampleFile)
        val schemaExample = SchemaExample.fromFile(exampleFile).value
        val exampleValue = schemaExample.value as JSONObjectValue

        assertThat(result.status).isEqualTo(FixExampleStatus.SUCCEDED)
        assertThat(schemaExample.discriminatorBasedOn).isNull()
        assertThat(schemaExample.schemaBasedOn).isEqualTo("Test")
        assertThat(exampleValue.findFirstChildByName("string")).isEqualTo(StringValue("OK"))
        assertThat(exampleValue.findFirstChildByName("number")).isInstanceOf(NumberValue::class.java)
        assertThat(exampleValue.findFirstChildByName("boolean")).isInstanceOf(BooleanValue::class.java)
        assertThat(exampleValue.findFirstChildByName("date")).isEqualTo(StringValue("(date)"))
    }
}