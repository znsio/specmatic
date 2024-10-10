package application.exampleGeneration.openApiExamples

import application.exampleGeneration.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.specmatic.conversions.convertPathParameterStyle
import io.specmatic.core.DEFAULT_TIMEOUT_IN_MILLISECONDS
import io.specmatic.core.Feature
import io.specmatic.core.Scenario
import io.specmatic.core.TestResult
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

    override val htmlTableColumns: List<HtmlTableColumn> = listOf (
        HtmlTableColumn(name = "path", colSpan = 2),
        HtmlTableColumn(name = "method", colSpan = 1),
        HtmlTableColumn(name = "response", colSpan = 1)
    )

    override fun testExternalExample(feature: Feature, exampleFile: File, testBaseUrl: String): Pair<TestResult, String> {
        val test = feature.createContractTestFromExampleFile(exampleFile.absolutePath).value

        val testResultRecord = test.runTest(testBaseUrl, timeoutInMilliseconds = DEFAULT_TIMEOUT_IN_MILLISECONDS).let {
            test.testResultRecord(it.first, it.second)
        } ?: return Pair(TestResult.Error, "TestResult record not found for example ${exampleFile.nameWithoutExtension}")

        return testResultRecord.scenarioResult?.let { scenarioResult ->
            Pair(testResultRecord.result, TestInteractionsLog.testHttpLogMessages.last { it.scenario == scenarioResult.scenario }.combineLog())
        } ?: Pair(TestResult.Error, "Interaction logs not found for example ${exampleFile.nameWithoutExtension}")
    }

    override suspend fun getScenarioFromRequestOrNull(call: ApplicationCall, feature: Feature): Scenario? {
        val request = call.receive<ExampleGenerationRequest>()
        return feature.scenarios.firstOrNull {
            it.method == request.method && it.status == request.response && it.path == request.path
                    && (request.contentType == null || it.httpRequestPattern.headersPattern.contentType == request.contentType)
        }
    }

    override fun createTableRows(scenarioExamplePair: List<Pair<Scenario, ExampleValidationResult?>>): List<TableRow> {
        val groupedScenarios = scenarioExamplePair.sortScenarios().groupScenarios()

        return groupedScenarios.flatMap { (_, methodMap) ->
            val pathSpan = methodMap.values.sumOf { it.size }
            val methodSet: MutableSet<String> = mutableSetOf()
            var showPath = true

            methodMap.flatMap { (method, scenarios) ->
                scenarios.map { (scenario, example) ->
                    TableRow(
                        columns = listOf(
                            TableRowGroup("path", convertPathParameterStyle(scenario.path), rawValue = scenario.path, rowSpan = pathSpan, showRow = showPath),
                            TableRowGroup("method", scenario.method, showRow = !methodSet.contains(method), rowSpan = scenarios.size),
                            TableRowGroup("response", scenario.status.toString(), showRow = true, rowSpan = 1, extraInfo = scenario.httpRequestPattern.headersPattern.contentType)
                        ),
                        exampleFilePath = example?.exampleFile?.absolutePath,
                        exampleFileName = example?.exampleName,
                        exampleMismatchReason = example?.result?.reportString().takeIf { reason -> reason?.isNotBlank() == true }
                    ).also { methodSet.add(method); showPath = false }
                }
            }
        }
    }

    private fun List<Pair<Scenario, ExampleValidationResult?>>.groupScenarios(): Map<String, Map<String, List<Pair<Scenario, ExampleValidationResult?>>>> {
        return this.groupBy { it.first.path }.mapValues { pathGroup ->
            pathGroup.value.groupBy { it.first.method }
        }
    }

    private fun List<Pair<Scenario, ExampleValidationResult?>>.sortScenarios(): List<Pair<Scenario, ExampleValidationResult?>> {
        return this.sortedBy {
            "${it.first.path}_${it.first.method}_${it.first.status}"
        }
    }

    data class ExampleGenerationRequest (
        val method: String,
        val path: String,
        val response: Int,
        val contentType: String? = null
    )
}