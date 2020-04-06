package run.qontract.core

fun testBackwardCompatibility(older: ContractBehaviour, newerContract: ContractBehaviour): Results =
        older.generateContractTests().fold(Results()) { results, olderScenario ->
            newerContract.setServerState(olderScenario.expectedFacts)

            val request = olderScenario.generateHttpRequest()

            try {
                val response = newerContract.lookup(request)

                val result = olderScenario.matches(response)
                results.copy(results = results.results.plus(Triple(result, request, response)).toMutableList())
            } catch (e: Throwable) {
                results.copy(results = results.results.plus(Triple(Result.Failure("Exception: ${e.localizedMessage}"), request, null)).toMutableList())
            }
        }
