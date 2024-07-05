package `in`.specmatic.test

import `in`.specmatic.conversions.convertPathParameterStyle
import `in`.specmatic.core.*
import `in`.specmatic.core.log.HttpLogMessage
import `in`.specmatic.core.log.LogMessage
import `in`.specmatic.core.log.logger
import `in`.specmatic.core.utilities.exceptionCauseMessage

data class ScenarioAsTest(
    val scenario: Scenario,
    private val flagsBased: FlagsBased,
    private val sourceProvider: String? = null,
    private val sourceRepository: String? = null,
    private val sourceRepositoryBranch: String? = null,
    private val specification: String? = null,
    private val serviceType: String? = null,
    private val annotations: String? = null,
    private val validators: List<ResponseValidator> = emptyList()
) : ContractTest {
    override fun testResultRecord(result: Result, response: HttpResponse?): TestResultRecord {
        val resultStatus = result.testResult()

        val responseStatus = scenario.getStatus(response)
        return TestResultRecord(
            convertPathParameterStyle(scenario.path),
            scenario.method,
            responseStatus,
            resultStatus,
            sourceProvider,
            sourceRepository,
            sourceRepositoryBranch,
            specification,
            serviceType,
            actualResponseStatus = response?.status ?: 0
        )
    }

    override fun testDescription(): String {
        return scenario.testDescription()
    }

    override fun runTest(testBaseURL: String, timeOut: Int): Pair<Result, HttpResponse?> {
        val log: (LogMessage) -> Unit = { logMessage ->
            logger.log(logMessage.withComment(this.annotations))
        }

        val httpClient = HttpClient(testBaseURL, log = log, timeout = timeOut)

        return runTest(httpClient)
    }

    override fun runTest(testExecutor: TestExecutor): Pair<Result, HttpResponse?> {

        val (result, response) = executeTestAndReturnResultAndResponse(scenario, testExecutor, flagsBased)
        return Pair(result.updateScenario(scenario), response)
    }

    override fun plusValidator(validator: ResponseValidator): ScenarioAsTest {
        return this.copy(
            validators = this.validators.plus(validator)
        )
    }

    private fun logComment() {
        if (annotations != null) {
            logger.log(annotations)
        }
    }

    private fun executeTestAndReturnResultAndResponse(
        testScenario: Scenario,
        testExecutor: TestExecutor,
        flagsBased: FlagsBased
    ): Pair<Result, HttpResponse?> {
        val request = testScenario.generateHttpRequest(flagsBased)

        return try {
            testExecutor.setServerState(testScenario.serverState)

            testExecutor.preExecuteScenario(testScenario, request)

            val response = testExecutor.execute(request)

            val validatorResult = validators.asSequence().map { it.validate(scenario, response) }.filterNotNull().firstOrNull()
            val result = validatorResult ?: testResult(request, response, testScenario, flagsBased)

            Pair(result.withBindings(testScenario.bindings, response), response)
        } catch (exception: Throwable) {
            Pair(
                Result.Failure(exceptionCauseMessage(exception))
                .also { failure -> failure.updateScenario(testScenario) }, null)
        }
    }

    private fun testResult(
        request: HttpRequest,
        response: HttpResponse,
        testScenario: Scenario,
        flagsBased: FlagsBased? = null
    ): Result {

        val result = when {
            response.specmaticResultHeaderValue() == "failure" -> Result.Failure(response.body.toStringLiteral())
                .updateScenario(testScenario)
            else -> testScenario.matches(request, response, ContractAndResponseMismatch, flagsBased?.unexpectedKeyCheck ?: ValidateUnexpectedKeys)
        }

        if (result is Result.Success && result.isPartialSuccess()) {
            logger.log("    PARTIAL SUCCESS: ${result.partialSuccessMessage}")
            logger.newLine()
        }

        return result
    }

}

private fun LogMessage.withComment(comment: String?): LogMessage {
    return if (this is HttpLogMessage) {
        this.copy(comment = comment)
    } else {
        this
    }
}
