package `in`.specmatic.core

import `in`.specmatic.core.pattern.ContractException
import kotlinx.coroutines.*
import java.util.concurrent.Executors

fun testBackwardCompatibility_old(older: Feature, newerBehaviour: Feature): Results {
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

fun testBackwardCompatibility(older: Feature, newerBehaviour: Feature): Results {
    return older.generateBackwardCompatibilityTestScenarios().filter { !it.ignoreFailure }.fold(Results()) { results, olderScenario ->
        val scenarioResults: List<Result> = testBackwardCompatibility(olderScenario, newerBehaviour)
        results.copy(results = results.results.plus(scenarioResults))
    }
}

fun testBackwardCompatibility(
    oldScenario: Scenario,
    newFeature_: Feature
): List<Result> {
    val newFeature = newFeature_.copy()

    return if (oldScenario.kafkaMessagePattern != null) {
        val scenarioMatchResults =
            newFeature.lookupKafkaScenario(oldScenario.kafkaMessagePattern, oldScenario.resolver)

        val result = Results(scenarioMatchResults.map { it.second }.toMutableList()).toResultIfAny()

        listOf(result)
    } else {
        newFeature.setServerState(oldScenario.expectedFacts)

        try {
            val request = oldScenario.generateHttpRequest()
            val newerScenarios = newFeature.lookupScenario(request)

            val httpScenarioResults = newerScenarios.map { newerScenario ->
                val newerResponsePattern = newerScenario.httpResponsePattern
                oldScenario.httpResponsePattern.encompasses(
                    newerResponsePattern,
                    oldScenario.resolver,
                    newerScenario.resolver
                ).also {
                    it.scenario = newerScenario
                }
            }

            httpScenarioResults.find { it is Result.Success }?.let { listOf(it) } ?: httpScenarioResults
        } catch (contractException: ContractException) {
            listOf(contractException.failure())
        } catch (stackOverFlowException: StackOverflowError) {
            listOf(Result.Failure("Exception: Stack overflow error, most likely caused by a recursive definition. Please report this with a sample contract as a bug!"))
        } catch (throwable: Throwable) {
            listOf(Result.Failure("Exception: ${throwable.localizedMessage}"))
        }
    }
}

