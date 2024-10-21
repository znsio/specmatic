package application.exampleGeneration.openApiExamples

import application.exampleGeneration.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.specmatic.conversions.convertPathParameterStyle
import io.specmatic.core.*
import io.specmatic.examples.*
import io.specmatic.test.TestInteractionsLog
import io.specmatic.test.TestInteractionsLog.combineLog
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File

@Command(name = "interactive", description = ["Generate, validate and test examples interactively through a Web UI"])
class OpenApiExamplesInteractive : ExamplesInteractiveBase<Feature, Scenario>(
    featureStrategy = OpenApiExamplesFeatureStrategy(), generationStrategy = OpenApiExamplesGenerationStrategy(), validationStrategy = OpenApiExamplesValidationStrategy()
) {
    @Option(names = ["--extensive"], description = ["Display all responses, not just 2xx, in the table."], defaultValue = "false")
    override var extensive: Boolean = false

    override val server: ExamplesInteractiveServer = ExamplesInteractiveServer(this)
    override val exampleTableColumns: List<ExampleTableColumn> = listOf (
        ExampleTableColumn(name = "path", colSpan = 2),
        ExampleTableColumn(name = "method", colSpan = 1),
        ExampleTableColumn(name = "response", colSpan = 1)
    )

    override fun testExternalExample(feature: Feature, exampleFile: File, testBaseUrl: String): Pair<Result, String> {
        return feature.createContractTestFromExampleFile(exampleFile).realise(
            orFailure = { it.toFailure() to "" }, orException = { it.toHasFailure().toFailure() to "" },
            hasValue = { test, _ ->
                val testResult = test.runTest(testBaseUrl, timeoutInMilliseconds = DEFAULT_TIMEOUT_IN_MILLISECONDS)
                val testLogs = TestInteractionsLog.testHttpLogMessages.lastOrNull {
                    it.scenario == testResult.first.scenario
                }?.combineLog() ?: "Test logs not found for example"
                testResult.first to testLogs
            }
        )
    }

    override suspend fun getScenarioFromRequestOrNull(call: ApplicationCall, feature: Feature): Scenario? {
        val request = call.receive<ExampleGenerationRequest>()
        return feature.scenarios.firstOrNull {
            it.method == request.method.value && it.status == request.response.value && it.path == request.path.rawValue
                    && (request.contentType == null || it.httpRequestPattern.headersPattern.contentType == request.contentType)
        }
    }

    override fun createTableRows(scenarioExamplePair: List<Pair<Scenario, ExampleValidationResult?>>): List<ExampleTableRow> {
        val groupedScenarios = scenarioExamplePair.sortScenarios().groupScenarios()

        return groupedScenarios.flatMap { (path, pathGroup) ->
            var showPath = true
            pathGroup.methods.flatMap { (method, methodGroup) ->
                var showMethod = true
                methodGroup.statuses.flatMap { (status, statusGroup) ->
                    statusGroup.examples.flatMap { (_, scenarioExamplePair) ->
                        var showStatus = true
                        scenarioExamplePair.map { (scenario, example) ->
                            ExampleTableRow(
                                columns = listOf(
                                    ExampleRowGroup("path", convertPathParameterStyle(path), rawValue = path, rowSpan = pathGroup.count, showRow = showPath),
                                    ExampleRowGroup("method", method, rowSpan = methodGroup.count, showRow = showMethod),
                                    ExampleRowGroup("response", status, rowSpan = scenarioExamplePair.size, showRow = showStatus, extraInfo = scenario.httpRequestPattern.headersPattern.contentType)
                                ),
                                exampleFilePath = example?.exampleFile?.absolutePath,
                                exampleFileName = example?.exampleName,
                                exampleMismatchReason = example?.result?.reportString().takeIf { reason -> reason?.isNotBlank() == true }
                            ).also { showPath = false; showMethod = false; showStatus = false }
                        }
                    }
                }
            }
        }
    }

    private fun List<Pair<Scenario, ExampleValidationResult?>>.groupScenarios(): Map<String, PathGroup> {
        return this.groupBy { it.first.path }.mapValues { (_, pathGroup) ->
            PathGroup(
                count = pathGroup.size,
                methods = pathGroup.groupBy { it.first.method }.mapValues { (_, methodGroup) ->
                    MethodGroup(
                        count = methodGroup.size,
                        statuses = methodGroup.groupBy { it.first.status.toString() }.mapValues { (_, statusGroup) ->
                            StatusGroup(count = statusGroup.size, examples = statusGroup.groupBy { it.first.httpRequestPattern.headersPattern.contentType })
                        }
                    )
                }
            )
        }
    }

    private fun List<Pair<Scenario, ExampleValidationResult?>>.sortScenarios(): List<Pair<Scenario, ExampleValidationResult?>> {
        return this.sortedBy {
            "${it.first.path}_${it.first.method}_${it.first.status}"
        }
    }

    data class ExampleGenerationRequest (
        val method: ValueWithInfo<String>,
        val path: ValueWithInfo<String>,
        val response: ValueWithInfo<Int>,
    ) {
        val contentType = response.extraInfo
    }

    data class StatusGroup(
        val count: Int,
        val examples: Map<String?, List<Pair<Scenario, ExampleValidationResult?>>>
    )

    data class MethodGroup (
        val count: Int,
        val statuses: Map<String, StatusGroup>
    )

    data class PathGroup (
        val count: Int,
        val methods: Map<String, MethodGroup>
    )

}