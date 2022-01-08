package `in`.specmatic.test

import `in`.specmatic.core.Result

class TargetHostAndPort(private val host: String?, private val port: String?): HTTPTestTargetInvoker {
    override fun execute(contractTest: ContractTest, timeout: Int): Result {
        return contractTest.runTest(host, port, timeout)
    }
}