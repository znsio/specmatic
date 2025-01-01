package io.specmatic.test

import io.specmatic.conversions.convertPathParameterStyle
import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpResponse
import io.specmatic.core.Result
import io.specmatic.core.Scenario
import io.specmatic.core.log.LogMessage
import io.specmatic.core.log.logger

class ScenarioTestGenerationFailure(
    var scenario: Scenario,
    val failure: Result.Failure
): ContractTest {

    init {
        val exampleRow = scenario.examples.flatMap { it.rows }.firstOrNull { it.name == failure.message }
        if (exampleRow != null) {
            scenario = scenario.copy(exampleRow = exampleRow, exampleName = failure.message)
        }
    }

    private val httpRequest: HttpRequest = scenario.exampleRow?.requestExample ?: HttpRequest(path = scenario.path, method = scenario.method)
    private val failureCause: Result.Failure = if (scenario.exampleRow != null && failure.cause != null) failure.cause else failure

    override fun toScenarioMetadata() = scenario.toScenarioMetadata()

    override fun testResultRecord(result: Result, response: HttpResponse?): TestResultRecord {
        return TestResultRecord(
            convertPathParameterStyle(scenario.path),
            scenario.method,
            scenario.status,
            result.testResult(),
            scenario.sourceProvider,
            scenario.sourceRepository,
            scenario.sourceRepositoryBranch,
            scenario.specification,
            scenario.serviceType,
            actualResponseStatus = 0,
            scenarioResult = result
        )
    }

    override fun testDescription(): String {
        return scenario.testDescription()
    }

    override fun runTest(testBaseURL: String, timeoutInMilliseconds: Long): Pair<Result, HttpResponse?> {
        val log: (LogMessage) -> Unit = { logMessage -> logger.log(logMessage) }
        val httpClient = HttpClient(testBaseURL, log = log, timeoutInMilliseconds = timeoutInMilliseconds)
        return runTest(httpClient)
    }

    override fun runTest(testExecutor: TestExecutor): Pair<Result, HttpResponse?> {
        testExecutor.preExecuteScenario(scenario, httpRequest)
        return Pair(failureCause.updateScenario(scenario), null)
    }

    override fun plusValidator(validator: ResponseValidator): ContractTest {
        return this
    }

}