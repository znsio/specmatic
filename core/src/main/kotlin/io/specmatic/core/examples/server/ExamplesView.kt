package io.specmatic.core.examples.server

import io.specmatic.conversions.ExampleFromFile
import io.specmatic.conversions.convertPathParameterStyle
import io.specmatic.core.Feature
import io.specmatic.core.Resolver
import io.specmatic.core.Result
import io.specmatic.core.Scenario
import io.specmatic.core.examples.server.ExamplesInteractiveServer.Companion.getExamplesFromDir
import io.specmatic.core.examples.server.ExamplesInteractiveServer.Companion.getExistingExampleFiles
import io.specmatic.core.pattern.*
import io.specmatic.core.value.NullValue
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import org.thymeleaf.templatemode.TemplateMode
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.io.File

class ExamplesView {
    companion object {
        fun getEndpoints(feature: Feature, examplesDir: File): List<Endpoint> {
            val examples = examplesDir.getExamplesFromDir()
            val scenarioExamplesPairList = getScenarioExamplesPairs(feature, examples)

            return scenarioExamplesPairList.map { (scenario, example) ->
                Endpoint(
                    path = convertPathParameterStyle(scenario.path),
                    rawPath = scenario.path,
                    method = scenario.method,
                    responseStatus = scenario.httpResponsePattern.status,
                    contentType = scenario.httpRequestPattern.headersPattern.contentType,
                    exampleFile = example?.first,
                    exampleMismatchReason = example?.second?.reportString()?.takeIf { it.isNotBlank() },
                    isPartialFailure = example?.second?.isPartialFailure() ?: false,
                    isDiscriminatorBased = scenario.isMultiGen(scenario.resolver)
                )
            }.filterEndpoints()
        }

        private fun Scenario.isMultiGen(resolver: Resolver): Boolean {
            val discriminatorInRequest = this.httpRequestPattern.body.isDiscriminatorBased(resolver)
            val discriminatorInResponse = this.httpResponsePattern.body.isDiscriminatorBased(resolver)
            return discriminatorInRequest || discriminatorInResponse
        }

        private fun Pattern.isDiscriminatorBased(resolver: Resolver): Boolean {
            return when (val resolvedPattern = resolvedHop(this, resolver)) {
                is AnyPattern -> resolvedPattern.isDiscriminatorPresent() && resolvedPattern.hasMultipleDiscriminatorValues()
                is ListPattern -> resolvedPattern.pattern.isDiscriminatorBased(resolver)
                else -> false
            }
        }

        private fun getScenarioExamplesPairs(feature: Feature, examples: List<ExampleFromFile>): List<Pair<Scenario, Pair<File, Result>?>> {
            return feature.scenarios.flatMap { scenario ->
                getExistingExampleFiles(feature, scenario, examples).map { exRes ->
                    scenario to Pair(exRes.first.file, exRes.second)
                }.ifEmpty { listOf(scenario to null) }
            }
        }

        private fun List<Endpoint>.filterEndpoints(): List<Endpoint> {
            return this.filter { it.responseStatus in 200..299 }
        }

        private fun List<Endpoint>.sortEndpoints(): List<Endpoint> {
            return this.sortedWith(compareBy({ it.path }, { it.method }, { it.responseStatus }))
        }

        private fun List<Endpoint>.groupEndpoints(): Map<String, PathGroup> {
            return this.groupBy { it.path }.mapValues { (_, pathGroup) ->
                PathGroup(
                    count = pathGroup.size,
                    methods = pathGroup.groupBy { it.method }.mapValues { (_, methodGroup) ->
                        MethodGroup(
                            count = methodGroup.size,
                            statuses = methodGroup.groupBy { it.responseStatus.toString() }.mapValues { (_, statusGroup) ->
                                StatusGroup(count = statusGroup.size, examples = statusGroup.groupBy { it.contentType })
                            }
                        )
                    }
                )
            }
        }

        fun List<Endpoint>.toTableRows(): List<TableRow> {
            val groupedEndpoint = this.sortEndpoints().groupEndpoints()
            return groupedEndpoint.flatMap { (_, pathGroup) ->
                var showPath = true
                pathGroup.methods.flatMap { (_, methodGroup) ->
                    var showMethod = true
                    methodGroup.statuses.flatMap { (_, statusGroup) ->
                        statusGroup.examples.flatMap { (_, examples) ->
                            var showStatus = true
                            examples.map {
                                TableRow(
                                    rawPath = it.rawPath,
                                    path = it.path,
                                    method = it.method,
                                    responseStatus = it.responseStatus.toString(),
                                    pathSpan = pathGroup.count,
                                    methodSpan = methodGroup.count,
                                    statusSpan = examples.size,
                                    showPath = showPath,
                                    showMethod = showMethod,
                                    showStatus = showStatus,
                                    contentType = it.contentType,
                                    example = it.exampleFile?.absolutePath,
                                    exampleName = it.exampleFile?.nameWithoutExtension,
                                    exampleMismatchReason = it.exampleMismatchReason?.takeIf { reason -> reason.isNotBlank() },
                                    isPartialFailure = it.isPartialFailure,
                                    isDiscriminatorBased = it.isDiscriminatorBased
                                ).also { showPath = false; showMethod = false; showStatus = false }
                            }
                        }
                    }
                }
            }
        }

        // SCHEMA EXAMPLE METHODS
        private fun getWithMissingDiscriminators(feature: Feature, mainPattern: String, examples: List<Triple<String, File?, Result?>>): List<Triple<String, File?, Result?>> {
            val discriminatorValues = feature.getAllDiscriminatorValues(mainPattern)
            if (discriminatorValues.isEmpty()) return examples

            return discriminatorValues.map { value ->
                examples.find { it.first == value && it.second != null } ?: Triple(value, null, null)
            }
        }

        private fun List<Pair<SchemaExample, Result?>>.groupByPattern(): Map<String, List<Pair<SchemaExample, Result?>>> {
            return this.groupBy { it.first.discriminatorBasedOn.takeIf { disc -> !disc.isNullOrEmpty() } ?: it.first.schemaBasedOn }
        }

        private fun Map<String, List<Pair<SchemaExample, Result?>>>.withMissingDiscriminators(feature: Feature): Map<String, List<Triple<String, File?, Result?>>> {
            return this.mapValues { (mainPattern, examples) ->
                val existingExample = examples.map { example ->
                    if (example.first.value is NullValue) {
                        Triple(example.first.schemaBasedOn, null, null)
                    } else Triple(example.first.schemaBasedOn, example.first.file, example.second)
                }
                getWithMissingDiscriminators(feature, mainPattern, existingExample)
            }
        }

        fun List<TableRow>.withSchemaExamples(feature: Feature, schemaExample: List<Pair<SchemaExample, Result?>>): List<TableRow> {
            val groupedSchemaExamples = schemaExample.groupByPattern()
            return groupedSchemaExamples.withMissingDiscriminators(feature).flatMap { (mainPattern, examples) ->
                val isDiscriminator = examples.size > 1
                examples.mapIndexed { index, (patternName, exampleFile, result) ->
                    TableRow(
                        rawPath = mainPattern,
                        path = mainPattern,
                        method = patternName.takeIf { isDiscriminator } ?: "",
                        responseStatus = "",
                        contentType = "",
                        pathSpan = examples.size,
                        methodSpan = 1,
                        statusSpan = 1,
                        showPath = index == 0,
                        showMethod = isDiscriminator,
                        showStatus = false,
                        example = exampleFile?.canonicalPath,
                        exampleName = exampleFile?.nameWithoutExtension,
                        exampleMismatchReason = result?.reportString()?.takeIf { it.isNotBlank() },
                        isPartialFailure = result?.isPartialFailure() ?: false,
                        isDiscriminatorBased = false, isSchemaBased = true,
                        pathColSpan = if (isDiscriminator) 3 else 5,
                        methodColSpan = if (isDiscriminator) 2 else 1
                    )
                }
            }.plus(this)
        }
    }
}

data class TableRow(
    val rawPath: String,
    val path: String,
    val method: String,
    val responseStatus: String,
    val contentType: String?,
    val pathSpan: Int,
    val methodSpan: Int,
    val statusSpan: Int,
    val showPath: Boolean,
    val showMethod: Boolean,
    val showStatus: Boolean,
    val example: String? = null,
    val exampleName: String? = null,
    val exampleMismatchReason: String? = null,
    val isPartialFailure: Boolean = false,
    val isGenerated: Boolean = exampleName != null,
    val isValid: Boolean = isGenerated && exampleMismatchReason == null,
    val uniqueKey: String = "${path}_${method}_${responseStatus}",
    val isDiscriminatorBased: Boolean,
    val isMainRow: Boolean = showPath || showMethod || showStatus,
    val isSchemaBased: Boolean = false,
    val pathColSpan: Int = 3,
    val methodColSpan: Int = 1
)

data class StatusGroup(
    val count: Int,
    val examples: Map<String?, List<Endpoint>>
)

data class MethodGroup (
    val count: Int,
    val statuses: Map<String, StatusGroup>
)

data class PathGroup (
    val count: Int,
    val methods: Map<String, MethodGroup>
)

data class Endpoint(
    val path: String,
    val rawPath: String,
    val method: String,
    val responseStatus: Int,
    val contentType: String? = null,
    val exampleFile: File? = null,
    val exampleMismatchReason: String? = null,
    val isPartialFailure: Boolean = false,
    val isDiscriminatorBased: Boolean
)

class HtmlTemplateConfiguration {
    companion object {
        private fun configureTemplateEngine(): TemplateEngine {
            val templateResolver = ClassLoaderTemplateResolver().apply {
                prefix = "templates/"
                suffix = ".html"
                templateMode = TemplateMode.HTML
                characterEncoding = "UTF-8"
            }

            return TemplateEngine().apply {
                setTemplateResolver(templateResolver)
            }
        }

        fun process(templateName: String, variables: Map<String, Any>): String {
            val templateEngine = configureTemplateEngine()
            return templateEngine.process(templateName, Context().apply {
                setVariables(variables)
            })
        }
    }
}

