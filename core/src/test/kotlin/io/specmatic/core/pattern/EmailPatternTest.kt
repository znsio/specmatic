package io.specmatic.core.pattern

import io.specmatic.GENERATION
import io.specmatic.conversions.OpenApiSpecification
import io.specmatic.core.EXAMPLES_DIR_SUFFIX
import io.specmatic.core.HttpRequest
import io.specmatic.core.Resolver
import io.specmatic.core.Result
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.StringValue
import io.specmatic.mock.ScenarioStub
import io.specmatic.stub.HttpStub
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.File

class EmailPatternTest {
    @Test
    @Tag(GENERATION)
    fun `negative values should be generated`() {
        val result = EmailPattern().negativeBasedOn(Row(), Resolver()).toList().map { it.value }
        assertThat(result.map { it.typeName }).containsExactlyInAnyOrder(
            "null",
            "number",
            "boolean",
            "string"
        )
    }

    @Test
    fun `email should not encompass string`() {
        assertThat(
            EmailPattern().encompasses(StringPattern(), Resolver(), Resolver(), emptySet())
        ).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `fillInTheBlanks should handle any-value pattern token correctly`() {
        val pattern = EmailPattern()
        val resolver = Resolver()
        val value = StringValue("(anyvalue)")

        val filledInValue = pattern.fillInTheBlanks(value, resolver).value
        val matchResult = pattern.matches(filledInValue, resolver)

        assertThat(matchResult.isSuccess()).withFailMessage(matchResult.reportString()).isTrue()
    }

    private fun File.getExternalExamplesFromContract(): List<ScenarioStub> {
        val attributeExamples = this.parentFile.resolve("${this.nameWithoutExtension}$EXAMPLES_DIR_SUFFIX")
        val normalExamples = this.parentFile.resolve("${this.nameWithoutExtension}${EXAMPLES_DIR_SUFFIX}_no_attr")

        return attributeExamples.listFiles()?.map { ScenarioStub.readFromFile(it) }?.plus(
            normalExamples.listFiles()?.map { ScenarioStub.readFromFile(it) } ?: emptyList()
        ) ?: emptyList()
    }

    @Test
    fun `should handle email format`() {
        val specFilepath = File("src/test/resources/openapi/spec_with_format_email_with_external_example/spec.yaml")

        val feature = OpenApiSpecification.fromFile(specFilepath.absolutePath).toFeature()
        val stubScenarios = specFilepath.getExternalExamplesFromContract()

        HttpStub(feature, stubScenarios).use {
            val response = it.client.execute(
                HttpRequest(
                "GET",
                "/pets/1"
                )
            )

            assertThat(response.status).isEqualTo(200)

            val body = (response.body as JSONObjectValue).jsonObject
            assertThat(body.keys).containsExactlyInAnyOrder("id", "name", "type", "status", "email")
        }
    }
}