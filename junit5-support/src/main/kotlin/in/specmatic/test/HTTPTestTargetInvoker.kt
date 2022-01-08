package `in`.specmatic.test

import `in`.specmatic.core.Result

interface HTTPTestTargetInvoker {
    fun execute(contractTest: ContractTest, timeout: Int): Result
}