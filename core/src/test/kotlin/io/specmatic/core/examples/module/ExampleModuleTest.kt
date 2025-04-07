package io.specmatic.core.examples.module

import io.specmatic.conversions.OpenApiSpecification
import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpResponse
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.mock.ScenarioStub
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID

class ExampleModuleTest {
    private val exampleModule = ExampleModule()

    @Test
    fun `get existing examples should be able to pick mutated and partial examples properly`(@TempDir tempDir: File) {
        val openApiSpec = """
          openapi: 3.0.3
          info:
            title: Sample API
            version: 1.0.0
          paths:
            /persons/{id}:
              patch:
                summary: Create an item in a category
                parameters:
                  - name: id
                    in: path
                    required: true
                    schema:
                      type: integer
                requestBody:
                  required: true
                  content:
                    application/json:
                      schema:
                        ${"$"}ref: '#/components/schemas/PersonBase'
                responses:
                  '200':
                    description: Created item
                    content:
                      application/json:
                        schema:
                          ${"$"}ref: '#/components/schemas/Person'
                  '400':
                    description: Invalid request
                    content:
                      application/json:
                        schema:
                          ${"$"}ref: '#/components/schemas/Error'
          components:
            schemas:
              PersonBase:
                type: object
                required:
                  - name
                  - age
                properties:
                  name:
                    type: string
                  age:
                    type: integer
              Person:
                allOf:
                  - ${"$"}ref: '#/components/schemas/PersonBase'
                  - type: object
                    required:
                      - id
                    properties:
                      id:
                        type: integer
              Error:
                type: object
                required:
                  - message
                properties:
                  message:
                    type: string
          """.trimIndent()
        val feature = OpenApiSpecification.fromYAML(openApiSpec, "api.yaml").toFeature()

        val examplesDir = File(tempDir, "api_examples").apply { mkdirs() }
        val successExamples = listOf(
            ScenarioStub(
                request = HttpRequest("PATCH", "/persons/123"),
                response = HttpResponse(status = 200)
            ),
            ScenarioStub(
                request = HttpRequest("PATCH", "/persons/abc"),
                response = HttpResponse(status = 200)
            )
        ).flatMap { it.toExamples(examplesDir) }
        val badRequestExamples = listOf(
            ScenarioStub(
                request = HttpRequest("PATCH", "/persons/123"),
                response = HttpResponse(status = 400)
            ),
            ScenarioStub(
                request = HttpRequest("PATCH", "/persons/abc"),
                response = HttpResponse(status = 400)
            )
        ).flatMap { it.toExamples(examplesDir) }

        val allExamples = exampleModule.getExamplesFromDir(examplesDir)
        val successScenarioExamples = exampleModule.getExistingExampleFiles(
            feature, feature.scenarios.first { it.status == 200 }, allExamples
        )
        val badRequestScenarioExamples = exampleModule.getExistingExampleFiles(
            feature, feature.scenarios.first { it.status == 400 }, allExamples
        )

        assertThat(successScenarioExamples).hasSize(4)
        assertThat(successScenarioExamples.map { it.first.file }).containsExactlyInAnyOrderElementsOf(successExamples)

        assertThat(badRequestScenarioExamples).hasSize(4)
        assertThat(badRequestScenarioExamples.map { it.first.file }).containsExactlyInAnyOrderElementsOf(badRequestExamples)
    }

    private fun ScenarioStub.toExamples(examplesDir: File): List<File> {
        val nonPartialExample = this.toJSON()
        val partialExample = JSONObjectValue(mapOf("partial" to nonPartialExample))
        val nonPartial = examplesDir.resolve("${UUID.randomUUID()}.json").apply { writeText(nonPartialExample.toStringLiteral()) }
        val partial = examplesDir.resolve("${UUID.randomUUID()}_partial.json").apply { writeText(partialExample.toStringLiteral()) }
        return listOf(nonPartial, partial)
    }
}