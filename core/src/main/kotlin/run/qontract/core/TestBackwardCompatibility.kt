package run.qontract.core

import run.qontract.core.pattern.ContractException

fun testBackwardCompatibility(older: ContractBehaviour, newerContract: ContractBehaviour): Results =
        older.generateTestScenarios().fold(Results()) { results, olderScenario ->
            newerContract.setServerState(olderScenario.expectedFacts)

            val request = olderScenario.generateHttpRequest()

            try {
                val response = newerContract.lookup(request)

                val result = when {
                    response.headers["X-Qontract-Result"] == "failure" -> Result.Failure(response.body ?: "")
                    else -> olderScenario.matches(response)
                }

                results.copy(results = results.results.plus(Triple(result, request, response)).toMutableList())
            }
            catch(contractException: ContractException) {
                results.copy(results = results.results.plus(Triple(contractException.result(), request, null)).toMutableList())
            }
            catch(throwable: Throwable) {
                results.copy(results = results.results.plus(Triple(Result.Failure("Exception: ${throwable.localizedMessage}"), request, null)).toMutableList())
            }
        }
