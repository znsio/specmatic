package `in`.specmatic.test

import `in`.specmatic.core.Result

interface ContractTest {
    fun generateTestScenarios(testVariables: Map<String, String>, testBaseURLs: Map<String, String>): List<ContractTest>
    fun testDescription(): String
    fun runTest(host: String?, port: String?, timeout: Int): Result
}
