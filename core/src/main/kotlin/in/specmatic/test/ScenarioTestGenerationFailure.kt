package `in`.specmatic.test

import `in`.specmatic.core.utilities.exceptionCauseMessage
import `in`.specmatic.core.Scenario
import `in`.specmatic.core.Result

class ScenarioTestGenerationFailure(val scenario: Scenario, val e: Throwable) : ContractTest {
    override fun testResultRecord(result: Result): TestResultRecord {
        return TestResultRecord(scenario.path, scenario.method, scenario.httpResponsePattern.status, result.testResult())
    }

    override fun generateTestScenarios(testVariables: Map<String, String>, testBaseURLs: Map<String, String>): List<ContractTest> {
        return listOf(this)
    }

    override fun testDescription(): String {
        return scenario.testDescription()
    }

    override fun runTest(host: String?, port: String?, timeout: Int): Result {
        return Result.Failure(exceptionCauseMessage(e)).updateScenario(scenario)
    }

    override fun runTest(testBaseURL: String?, timeOut: Int): Result {
        return Result.Failure(exceptionCauseMessage(e)).updateScenario(scenario)
    }
}
