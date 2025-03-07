package io.specmatic.core.examples.module

import io.specmatic.conversions.ExampleFromFile
import io.specmatic.core.Feature
import io.specmatic.core.Result
import io.specmatic.core.Scenario
import io.specmatic.core.examples.server.FixExampleRequest
import io.specmatic.core.examples.server.FixExampleResponse
import io.specmatic.core.examples.server.FixExampleResult
import io.specmatic.core.examples.server.FixExampleStatus
import io.specmatic.core.examples.server.SchemaExample
import io.specmatic.core.parseContractFileToFeature
import io.specmatic.core.pattern.HasFailure
import io.specmatic.core.utilities.exceptionCauseMessage
import io.specmatic.mock.ScenarioStub
import java.io.File

class ExampleFixModule(
    private val exampleValidationModule: ExampleValidationModule
) {

    fun fixExample(contractFile: File, request: FixExampleRequest): FixExampleResponse {
        try {
            val feature = parseContractFileToFeature(contractFile)
            fixExample(feature, request.exampleFile)
            return FixExampleResponse(exampleFile = request.exampleFile)
        } catch(e: Throwable) {
            return FixExampleResponse(exampleFile = request.exampleFile, errorMessage = exceptionCauseMessage(e))
        }
    }

    fun fixExample(feature: Feature, exampleFile: File): FixExampleResult {
        val exampleReturnValue = ExampleFromFile.fromFile(exampleFile)

        if(exampleReturnValue is HasFailure<ExampleFromFile>) {
            return fixSchemaExample(feature, exampleFile)
        }

        return fixExampleIn(exampleFile, feature, exampleReturnValue.value)
    }

    private fun fixSchemaExample(
        feature: Feature,
        exampleFile: File
    ): FixExampleResult {
        if (exampleValidationModule.validateSchemaExample(feature, exampleFile) is Result.Success) {
            return FixExampleResult(status = FixExampleStatus.SKIPPED, exampleFileName = exampleFile.name)
        }
        fixSchemaExampleAndWriteTo(exampleFile, feature)
        return FixExampleResult(status = FixExampleStatus.SUCCEDED, exampleFileName = exampleFile.name)
    }

    private fun fixExampleIn(
        exampleFile: File,
        feature: Feature,
        example: ExampleFromFile
    ): FixExampleResult {
        val matchingHttpPathPattern = feature.matchingHttpPathPatternFor(
            example.requestPath.orEmpty()
        ) ?: throw Exception("No scenario found for request path in '${exampleFile.name}'.")

        val scenario = feature.scenarioAssociatedTo(
            method = example.requestMethod.orEmpty(),
            path = matchingHttpPathPattern.path,
            responseStatusCode = example.responseStatus ?: 0,
            contentType = example.requestContentType
        ) ?: throw Exception("No scenario found for example '${exampleFile.name}'.")

        if (exampleValidationModule.validateExample(feature, exampleFile) is Result.Success) {
            return FixExampleResult(status = FixExampleStatus.SKIPPED, exampleFileName = exampleFile.name)
        }

        fixExampleAndWriteTo(exampleFile, scenario, feature)
        return FixExampleResult(status = FixExampleStatus.SUCCEDED, exampleFileName = exampleFile.name)
    }

    private fun fixExampleAndWriteTo(exampleFile: File, scenario: Scenario, feature: Feature) {
        val example = ScenarioStub.readFromFile(exampleFile)
        val (fixedRequest, fixedResponse) = scenario.fixRequestResponse(
            httpRequest = example.request,
            httpResponse = example.response,
            flagsBased = feature.flagsBased
        )
        val fixedExampleJson = example.copy(
            request = fixedRequest,
            response = fixedResponse
        ).toJSON().toStringLiteral()

        exampleFile.writeText(fixedExampleJson)
    }

    private fun fixSchemaExampleAndWriteTo(exampleFile: File, feature: Feature) {
        val schemaExample = SchemaExample.fromFile(exampleFile).value
        val fixedExample = feature.fixSchemaFlagBased(
            schemaExample.discriminatorBasedOn,
            schemaExample.schemaBasedOn,
            schemaExample.value
        )
        schemaExample.file.writeText(fixedExample.toStringLiteral())
    }
}