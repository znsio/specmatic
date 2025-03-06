package io.specmatic.core.examples.module

import io.specmatic.conversions.ExampleFromFile
import io.specmatic.core.AttributeSelectionPatternDetails
import io.specmatic.core.DiscriminatorBasedRequestResponse
import io.specmatic.core.Feature
import io.specmatic.core.HttpRequest
import io.specmatic.core.Scenario
import io.specmatic.core.discriminator.DiscriminatorExampleInjector
import io.specmatic.core.discriminator.DiscriminatorMetadata
import io.specmatic.core.examples.server.ExampleModule
import io.specmatic.core.examples.server.ExamplesView.Companion.isScenarioMultiGen
import io.specmatic.core.examples.server.ScenarioFilter
import io.specmatic.core.examples.server.SchemaExample.Companion.toSchemaExampleFileName
import io.specmatic.core.examples.server.SchemaExamplesView.Companion.schemaExamplesToTableRows
import io.specmatic.core.log.consoleLog
import io.specmatic.core.log.logger
import io.specmatic.core.parseContractFileToFeature
import io.specmatic.core.utilities.uniqueNameForApiOperation
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.Value
import io.specmatic.mock.ScenarioStub
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class ExampleGenerationModule(
    private val exampleModule: ExampleModule
) {
    private val exampleFileNamePostFixCounter = AtomicInteger(0)

    fun resetExampleFileNameCounter() {
        exampleFileNamePostFixCounter.set(0)
    }

    fun generate(contractFile: File, scenarioFilter: ScenarioFilter, allowOnlyMandatoryKeysInJSONObject: Boolean): List<String> {
        return try {
            val feature: Feature = parseContractFileToFeature(contractFile)
                .let { feature ->
                    val filteredScenarios = feature.scenarios

                    scenarioFilter.filter(feature.copy(scenarios = filteredScenarios.map {
                        it.copy(examples = emptyList())
                    })).copy(stubsFromExamples = emptyMap())
                }
            if (feature.scenarios.isEmpty()) {
                logger.log("All examples were filtered out by the filter expression")
                return emptyList()
            }

            val examplesDir = exampleModule.getExamplesDirPath(contractFile).also { if (it.exists()) it.mkdirs() }
            val allExistingExamples = exampleModule.getExamplesFromDir(examplesDir)

            val schemaExamples = schemaExamplesToTableRows(feature, exampleModule.getSchemaExamplesWithValidation(feature, examplesDir)).flatMap {
                if (it.example != null) {
                    listOf(ExamplePathInfo(path = it.example, created = false, status = ExampleGenerationStatus.EXISTED))
                } else generateForSchemaBased(contractFile, it.rawPath, it.method)
            }

            val allExamples = feature.scenarios.flatMap { scenario ->
                generateAndLogScenarioExamples(contractFile, feature, scenario, allExistingExamples, allowOnlyMandatoryKeysInJSONObject)
            }.plus(schemaExamples)

            generationSummary(contractFile, allExamples).map { it.path }
        } catch (e: StackOverflowError) {
            logger.log("Got a stack overflow error. You probably have a recursive data structure definition in the contract.")
            throw e
        }
    }

    fun generate(
        contractFile: File,
        method: String,
        path: String,
        responseStatusCode: Int,
        contentType: String? = null,
        bulkMode: Boolean = false,
        allowOnlyMandatoryKeysInJSONObject: Boolean
    ): List<ExamplePathInfo> {
        val feature = parseContractFileToFeature(contractFile)
        val scenario = feature.scenarioAssociatedTo(method, path, responseStatusCode, contentType)
        if(scenario == null) return emptyList()

        val examplesDir = exampleModule.getExamplesDirPath(contractFile)
        val examples = exampleModule.getExamplesFromDir(examplesDir)

        return generateAndLogScenarioExamples(
            contractFile, feature, scenario,
            allExistingExamples = if(bulkMode) examples else emptyList(),
            allowOnlyMandatoryKeysInJSONObject
        )
    }

    fun generateForSchemaBased(contractFile: File, path: String, method: String): List<ExamplePathInfo> {
        val examplesDir = exampleModule.getExamplesDirPath(contractFile)
        if(examplesDir.exists().not()) examplesDir.mkdirs()

        val feature = parseContractFileToFeature(contractFile)
        val (discriminatorPatternName, patternName) = when {
            method.isEmpty() -> null to path
            else -> path to method
        }

        val value = feature.generateSchemaFlagBased(discriminatorPatternName, patternName)
        val schemaFileName = toSchemaExampleFileName(discriminatorPatternName, patternName)

        val exampleFile = exampleModule.getSchemaExamples(examplesDir).firstOrNull {
            it.file.nameWithoutExtension == schemaFileName
        }?.file ?: examplesDir.resolve(schemaFileName)

        println("Writing to file: ${exampleFile.relativeTo(contractFile.canonicalFile.parentFile).path}")
        exampleFile.writeText(value.toStringLiteral())
        return listOf(ExamplePathInfo(path = exampleFile.absolutePath, created = true))
    }

    private fun generateExampleFiles(
        contractFile: File,
        feature: Feature,
        scenario: Scenario,
        allowOnlyMandatoryKeysInJSONObject: Boolean,
        existingExamples: List<ExampleFromFile>
    ): List<ExamplePathInfo> {
        val examplesDir = exampleModule.getExamplesDirPath(contractFile)
        if(!examplesDir.exists()) examplesDir.mkdirs()

        val discriminatorBasedRequestResponses = generate2xxRequestResponseList(
            feature,
            scenario,
            allowOnlyMandatoryKeysInJSONObject
        )

        val requestDiscriminator = discriminatorBasedRequestResponses.first().requestDiscriminator
        val responseDiscriminator = discriminatorBasedRequestResponses.first().responseDiscriminator

        val existingDiscriminators = existingExamples.map {
            it.requestBody?.getDiscriminatorValue(requestDiscriminator).orEmpty() to it.responseBody?.getDiscriminatorValue(responseDiscriminator).orEmpty()
        }.toSet()

        return discriminatorBasedRequestResponses.filterNot { it.matches(existingDiscriminators) }.map { (request, response, requestDiscriminator, responseDiscriminator) ->
            val requestWithoutAttrSelection = request.removeAttrSelection(scenario.attributeSelectionPattern)

            val scenarioStub = ScenarioStub(requestWithoutAttrSelection, response)
            val jsonWithDiscriminator = DiscriminatorExampleInjector(
                stubJSON = scenarioStub.toJSON(),
                requestDiscriminator = requestDiscriminator,
                responseDiscriminator = responseDiscriminator
            ).getExampleWithDiscriminator()

            val uniqueNameForApiOperation = getExampleFileNameBasedOn(
                requestDiscriminator,
                responseDiscriminator,
                scenarioStub
            )

            val file = examplesDir.resolve("${uniqueNameForApiOperation}_${exampleFileNamePostFixCounter.incrementAndGet()}.json")
            println("Writing to file: ${file.relativeTo(contractFile.canonicalFile.parentFile).path}")
            file.writeText(jsonWithDiscriminator.toStringLiteral())
            ExamplePathInfo(file.absolutePath, true)
        }
    }

    private fun generate2xxRequestResponseList(
        feature: Feature,
        scenario: Scenario,
        allowOnlyMandatoryKeysInJSONObject: Boolean
    ): List<DiscriminatorBasedRequestResponse> {
        if(scenario.isA2xxScenario().not()) return emptyList()
        return feature
            .generateDiscriminatorBasedRequestResponseList(
                scenario,
                allowOnlyMandatoryKeysInJSONObject = allowOnlyMandatoryKeysInJSONObject
            ).map { it.copy(response = it.response.withoutSpecmaticResultHeader()) }
    }

    private fun generationSummary(contractFile: File, exampleFiles: List<ExamplePathInfo>): List<ExamplePathInfo> {
        val resultCounts = exampleFiles.groupBy { it.status }.mapValues { it.value.size }
        val createdFileCount = resultCounts[ExampleGenerationStatus.CREATED] ?: 0
        val existingFileCount = resultCounts[ExampleGenerationStatus.EXISTED] ?: 0

        logger.log(System.lineSeparator() + "NOTE: All examples may be found in ${exampleModule.getExamplesDirPath(contractFile).canonicalFile}" + System.lineSeparator())
        logger.log("=============== Example Generation Summary ===============")
        logger.log("$createdFileCount example(s) created, $existingFileCount examples already existed")
        logger.log("==========================================================")

        return exampleFiles
    }

    private fun generateAndLogScenarioExamples(contractFile: File, feature: Feature, scenario: Scenario, allExistingExamples: List<ExampleFromFile>, allowOnlyMandatoryKeysInJSONObject: Boolean): List<ExamplePathInfo> {
        return try {
            val trimmedDescription = scenario.testDescription().trim()
            val existingExamples = exampleModule.getExistingExampleFiles(feature, scenario, allExistingExamples).map { it.first }
            val scenarioIsMultiGen = isScenarioMultiGen(scenario, scenario.resolver)

            val generatedExamples = if (scenarioIsMultiGen || existingExamples.isEmpty()) {
                generateExampleFiles(contractFile, feature, scenario, allowOnlyMandatoryKeysInJSONObject, existingExamples)
            } else emptyList()

            val allExamples = existingExamples.map {
                ExamplePathInfo(it.file.canonicalPath, created = false)
            }.plus(generatedExamples)

            allExamples.also { logExamples(contractFile, trimmedDescription, it) }
        } catch (e: Throwable) {
            logger.log(e, "Exception generating example for ${scenario.testDescription()}")
            emptyList()
        }
    }

    private fun logExamples(contractFile: File, description: String, examples: List<ExamplePathInfo>) {
        examples.forEach { example ->
            val loggablePath = example.relativeTo(contractFile)
            if (!example.created) {
                consoleLog("Example already existed for $description: $loggablePath")
            } else {
                consoleLog("Created example for $description: $loggablePath")
            }
        }
    }

    private fun getExampleFileNameBasedOn(
        requestDiscriminator: DiscriminatorMetadata,
        responseDiscriminator: DiscriminatorMetadata,
        scenarioStub: ScenarioStub
    ): String {
        val discriminatorValue = requestDiscriminator.discriminatorValue.ifBlank {
            responseDiscriminator.discriminatorValue
        }
        val discriminatorName = if (discriminatorValue.isNotEmpty()) "${discriminatorValue}_" else ""
        return discriminatorName + uniqueNameForApiOperation(
            scenarioStub.request,
            "",
            scenarioStub.response.status
        )
    }

    private fun HttpRequest.removeAttrSelection(attributeSelectionPattern: AttributeSelectionPatternDetails): HttpRequest {
        return this.copy(
            queryParams = this.queryParams.remove(attributeSelectionPattern.getQueryParamKey())
        )
    }

    private fun Value.getDiscriminatorValue(discriminator: DiscriminatorMetadata): String? {
        return when (this) {
            is JSONObjectValue -> {
                val targetValue = this.getEventValue() ?: this
                targetValue.findFirstChildByPath(discriminator.discriminatorProperty)?.toStringLiteral()
            }
            is JSONArrayValue -> this.list.first().getDiscriminatorValue(discriminator)
            else -> null
        }
    }

    private fun JSONObjectValue.getEventValue(): JSONObjectValue? {
        return (this.findFirstChildByPath("event") as? JSONObjectValue)?.let { eventValue ->
            eventValue.findFirstChildByPath(eventValue.jsonObject.keys.first()) as? JSONObjectValue
        }
    }

    private fun DiscriminatorBasedRequestResponse.matches(discriminatorValues: Set<Pair<String, String>>): Boolean {
        return discriminatorValues.contains(requestDiscriminator.discriminatorValue to responseDiscriminator.discriminatorValue)
    }
}