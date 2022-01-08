package `in`.specmatic.test

import `in`.specmatic.core.Result

class TargetBaseURL(private val testBaseURL: String): HTTPTestTargetInvoker {
    override fun execute(contractTest: ContractTest, timeout: Int): Result {
        return contractTest.runTest(testBaseURL, timeout)
    }
}