package application.exampleGeneration.openApiExamples

import application.exampleGeneration.ExamplesInteractiveBase
import application.exampleGeneration.HtmlTableColumn
import application.exampleGeneration.TableRow
import application.exampleGeneration.TableRowGroup
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

@Command(
    name = "interactive",
    mixinStandardHelpOptions = true,
    description = ["Generate and validate examples interactively through a Web UI"],
)
class OpenApiExamplesInteractive : ExamplesInteractiveBase<Feature, Scenario>(OpenApiExamplesCommon()) {
    @Option(names = ["--extensive"], description = ["Display all responses, not just 2xx, in the table."], defaultValue = "false")
    override var extensive: Boolean = false

    override val htmlTableColumns: List<HtmlTableColumn> = listOf(
        HtmlTableColumn(name = "path", colSpan = 2),
        HtmlTableColumn(name = "method", colSpan = 1),
        HtmlTableColumn(name = "response", colSpan = 1)
    )

    override fun testExternalExample(feature: Feature, exampleFile: File, testBaseUrl: String): Pair<TestResult, String> {
        val contractTests = feature.loadExternalisedExamples().generateContractTests(emptyList())

        val test = contractTests.firstOrNull {
            it.testDescription().contains(exampleFile.nameWithoutExtension)
        } ?: return Pair(TestResult.Error, "Test not found for example ${exampleFile.nameWithoutExtension}")

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

    override fun createTableRows(scenarios: List<Scenario>, exampleFiles: List<File>): List<TableRow> {
        val groupedScenarios = scenarios.sortScenarios().groupScenarios()

        return groupedScenarios.flatMap { (_, methodMap) ->
            val pathSpan = methodMap.values.sumOf { it.size }
            val methodSet: MutableSet<String> = mutableSetOf()
            var showPath = true

            methodMap.flatMap { (method, scenarios) ->
                scenarios.map {
                    val existingExample = common.getExistingExampleOrNull(it, exampleFiles)

                    TableRow(
                        columns = listOf(
                            TableRowGroup("path", convertPathParameterStyle(it.path), rawValue = it.path, rowSpan = pathSpan, showRow = showPath),
                            TableRowGroup("method", it.method, showRow = !methodSet.contains(method), rowSpan = scenarios.size),
                            TableRowGroup("response", it.status.toString(), showRow = true, rowSpan = 1, extraInfo = it.httpRequestPattern.headersPattern.contentType)
                        ),
                        exampleFilePath = existingExample?.first?.absolutePath,
                        exampleFileName = existingExample?.first?.nameWithoutExtension,
                        exampleMismatchReason = existingExample?.second?.reportString().takeIf { reason -> reason?.isNotBlank() == true }
                    ).also { methodSet.add(method); showPath = false }
                }
            }
        }
    }

    private fun List<Scenario>.groupScenarios(): Map<String, Map<String, List<Scenario>>> {
        return this.groupBy { it.path }.mapValues { pathGroup ->
            pathGroup.value.groupBy { it.method }
        }
    }

    private fun List<Scenario>.sortScenarios(): List<Scenario> {
        return this.sortedBy {
            "${it.path}_${it.method}_${it.status}"
        }
    }

    data class ExampleGenerationRequest (
        val method: String,
        val path: String,
        val response: Int,
        val contentType: String? = null
    )
}