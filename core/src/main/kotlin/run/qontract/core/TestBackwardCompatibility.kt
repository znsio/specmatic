package run.qontract.core

fun testBackwardCompatibility(older: ContractBehaviour, newer: ContractBehaviour): ExecutionInfo {
    val contractTests: List<Scenario> = older.generateContractTests()

     val executionInfo = ExecutionInfo()

    contractTests.forEach { testScenario ->
        newer.setServerState(testScenario.expectedFacts)

        try {
            val request = testScenario.generateHttpRequest()
            val response = newer.lookup(request)
            when(val result = testScenario.matches(response)) {
                is Result.Failure -> executionInfo.recordUnsuccessfulInteraction(result.scenario, result.stackTrace(), request, response)
                else -> executionInfo.recordSuccessfulInteraction()
            }
        } catch (e: Throwable) {
            executionInfo.recordInteractionError(testScenario, e)
        }

    }

    return executionInfo
}