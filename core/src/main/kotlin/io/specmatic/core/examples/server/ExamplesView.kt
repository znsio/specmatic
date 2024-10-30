package io.specmatic.core.examples.server

import io.specmatic.conversions.ExampleFromFile
import io.specmatic.conversions.convertPathParameterStyle
import io.specmatic.core.Feature
import io.specmatic.core.Scenario
import io.specmatic.core.examples.server.ExamplesInteractiveServer.Companion.getExamplesFromDir
import io.specmatic.core.examples.server.ExamplesInteractiveServer.Companion.getExistingExampleFiles
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
                    exampleMismatchReason = example?.second
                )
            }.filterEndpoints()
        }

        private fun getScenarioExamplesPairs(feature: Feature, examples: List<ExampleFromFile>): List<Pair<Scenario, Pair<File, String>?>> {
            return feature.scenarios.flatMap { scenario ->
                getExistingExampleFiles(feature, scenario, examples).map { exRes ->
                    scenario to exRes
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
                                    exampleMismatchReason = it.exampleMismatchReason?.takeIf { reason ->  reason.isNotBlank() }
                                ).also { showPath = false; showMethod = false; showStatus = false }
                            }
                        }
                    }
                }
            }
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
    val isGenerated: Boolean = exampleName != null,
    val isValid: Boolean = isGenerated && exampleMismatchReason == null,
    val uniqueKey: String = "${path}_${method}_${responseStatus}"
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
    val exampleMismatchReason: String? = null
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

