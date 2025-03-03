package io.specmatic.test

import io.specmatic.conversions.convertPathParameterStyle
import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpResponse
import io.specmatic.core.Result
import io.specmatic.core.Scenario
import io.specmatic.core.log.LogMessage
import io.specmatic.core.log.logger
import io.specmatic.core.pattern.ContractException
import io.specmatic.core.utilities.exceptionCauseMessage

class ScenarioTestGenerationException(
    var scenario: Scenario,
    val e: Throwable,
    val message: String,
    val breadCrumb: String?
) : ContractTest {

    init {
        val exampleRow = scenario.examples.flatMap { it.rows }.firstOrNull { it.name == message }
        if (exampleRow != null) {
            scenario = scenario.copy(exampleRow = exampleRow, exampleName = message)
        }
    }

    private val httpRequest: HttpRequest = scenario.exampleRow?.requestExample ?: HttpRequest(path = scenario.path, method = scenario.method)
    private val errorMessage: String = if (scenario.exampleRow != null && e is ContractException) "" else message

    override fun toScenarioMetadata() = scenario.toScenarioMetadata()

    override fun testResultRecord(result: Result, response: HttpResponse?): TestResultRecord {
        return TestResultRecord(
            convertPathParameterStyle(scenario.path),
            scenario.method,
            scenario.requestContentType,
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
        return error()
    }

    override fun plusValidator(validator: ResponseValidator): ContractTest {
        return this
    }

    fun error(): Pair<Result, HttpResponse?> {
        val result: Result = when(e) {
            is ContractException -> Result.Failure(errorMessage, e.failure(), breadCrumb = breadCrumb ?: "").updateScenario(scenario)
            else -> Result.Failure(errorMessage + " - " + exceptionCauseMessage(e), breadCrumb = breadCrumb ?: "").updateScenario(scenario)
        }

        return Pair(result, null)
    }
}

