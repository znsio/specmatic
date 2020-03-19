package run.qontract.core

fun testBackwardCompatibility(older: ContractBehaviour, newer: ContractBehaviour): ExecutionInfo {
    val contractTests: List<Scenario> = older.generateContractTests()

    val results = contractTests.map { testScenario ->
        newer.setServerState(testScenario.expectedState)

        val request = testScenario.generateHttpRequest()
        val response = newer.lookup(request)
        val result = testScenario.matches(response)

        Triple(result, request, response)
    }

    return ExecutionInfo(results)
}