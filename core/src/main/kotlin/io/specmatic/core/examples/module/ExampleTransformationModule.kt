package io.specmatic.core.examples.module

import io.specmatic.conversions.ExampleFromFile
import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpResponse
import io.specmatic.core.NoBodyValue
import io.specmatic.core.Scenario
import io.specmatic.core.examples.server.ExampleModule
import io.specmatic.core.examples.server.InteractiveExamplesMismatchMessages
import io.specmatic.core.log.consoleDebug
import io.specmatic.core.parseContractFileToFeature
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.ScalarValue
import io.specmatic.core.value.StringValue
import io.specmatic.core.value.Value
import io.specmatic.mock.MOCK_HTTP_REQUEST
import io.specmatic.mock.MOCK_HTTP_RESPONSE
import java.io.File

class ExampleTransformationModule(
    private val exampleModule: ExampleModule
) {

    fun transformExistingExamples(contractFile: File, overlayFile: File?, examplesDir: File) {
        val feature = parseContractFileToFeature(contractPath = contractFile.absolutePath, overlayContent = overlayFile?.readText().orEmpty())
        val examples = exampleModule.getExamplesFromDir(examplesDir)

        examples.forEach { example ->
            consoleDebug("\nTransforming ${example.file.nameWithoutExtension}")

            if (example.request.body.isScalarOrEmpty() && example.response.body.isScalarOrEmpty()) {
                consoleDebug("Skipping ${example.file.name}, both request and response bodies are scalars")
                return@forEach
            }

            val scenario = feature.matchResultFlagBased(example.request, example.response, InteractiveExamplesMismatchMessages)
                .toResultIfAny().takeIf { it.isSuccess() }?.scenario as? Scenario

            if (scenario == null) {
                consoleDebug("Skipping ${example.file.name}, no matching scenario found")
                return@forEach
            }

            val flagBasedResolver = feature.flagsBased.update(scenario.resolver)
            val requestWithoutOptionality = scenario.httpRequestPattern.withoutOptionality(example.request, flagBasedResolver)
            val responseWithoutOptionality = scenario.httpResponsePattern.withoutOptionality(example.response, flagBasedResolver)

            val updatedExample = example.replaceWithDescriptions(requestWithoutOptionality, responseWithoutOptionality)
            consoleDebug("Writing transformed example to ${example.file.canonicalFile.relativeTo(contractFile).path}")
            example.file.writeText(updatedExample.toStringLiteral())
            consoleDebug("Successfully written transformed example")
        }
    }

    private fun ExampleFromFile.replaceWithDescriptions(request: HttpRequest, response: HttpResponse): JSONObjectValue {
        return this.json.jsonObject.mapValues { (key, value) ->
            when (key) {
                MOCK_HTTP_REQUEST -> request.toJSON().insertFieldsInValue(value.getDescriptionMap())
                MOCK_HTTP_RESPONSE -> response.toJSON().insertFieldsInValue(value.getDescriptionMap())
                else -> value
            }
        }.let { JSONObjectValue(it.toMap()) }
    }

    private fun Value.isScalarOrEmpty(): Boolean {
        return this is ScalarValue || this is NoBodyValue
    }

    private fun Value.insertFieldsInValue(fieldsToBeInserted: Map<String, Value>): Value {
        return when (this) {
            is JSONObjectValue -> JSONObjectValue(fieldsToBeInserted.plus(this.jsonObject))
            is JSONArrayValue -> JSONArrayValue(this.list.map { value ->  value.insertFieldsInValue(fieldsToBeInserted) })
            else -> this
        }
    }

    private fun Value.getDescriptionMap(): Map<String, Value> {
        return (this as? JSONObjectValue)
            ?.findFirstChildByPath("description")
            ?.let { mapOf("description" to it.toStringLiteral()).toValueMap() }
            ?: emptyMap()
    }

    private fun Map<String, String>.toValueMap(): Map<String, Value> {
        return this.mapValues { StringValue(it.value) }
    }
}