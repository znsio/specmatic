package run.qontract.core

import run.qontract.core.pattern.ContractException

fun testBackwardCompatibility(older: Feature, newerBehaviour: Feature): Results {
    return older.generateBackwardCompatibilityTestScenarios().filter { !it.ignoreFailure }.fold(Results()) { results, olderScenario ->
        if(olderScenario.kafkaMessagePattern != null) {
            val scenarioMatchResults = newerBehaviour.lookupKafkaScenario(olderScenario.kafkaMessagePattern, olderScenario.resolver)

            val result = Results(scenarioMatchResults.map { it.second }.toMutableList()).toResultIfAny()

            results.copy(results = results.results.plus(result).toMutableList())
        } else {
            newerBehaviour.setServerState(olderScenario.expectedFacts)

            try {
                val request = olderScenario.generateHttpRequest()
                val newerScenarios = newerBehaviour.lookupScenario(request)

                val scenarioResults = newerScenarios.map { newerScenario ->
                    val newerResponsePattern = newerScenario.httpResponsePattern
                    olderScenario.httpResponsePattern.encompasses(newerResponsePattern, olderScenario.resolver, newerScenario.resolver).also {
                        it.scenario = newerScenario
                    }
                }

                val resultsToAdd = scenarioResults.find { it is Result.Success }?.let { listOf(it) } ?: scenarioResults
                results.copy(results = results.results.plus(resultsToAdd).toMutableList())
            } catch (contractException: ContractException) {
                results.copy(results = results.results.plus(contractException.failure()).toMutableList())
            } catch (stackOverFlowException: StackOverflowError) {
                results.copy(results = results.results.plus(Result.Failure("Exception: Stack overflow error, most likely caused by a recursive definition. Please report this with a sample contract as a bug!")).toMutableList())
            } catch (throwable: Throwable) {
                results.copy(results = results.results.plus(Result.Failure("Exception: ${throwable.localizedMessage}")).toMutableList())
            }
        }
    }
}
