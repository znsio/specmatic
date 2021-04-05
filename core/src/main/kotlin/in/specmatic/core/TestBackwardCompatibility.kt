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

fun testBackwardCompatibilitySerially(older: Feature, newerBehaviour: Feature): Results {
    return older.generateBackwardCompatibilityTestScenarios().filter { !it.ignoreFailure }.fold(Results()) { results, olderScenario ->
        val scenarioResults: List<Result> = testBackwardCompatibility(olderScenario, newerBehaviour)
        results.copy(results = results.results.plus(scenarioResults))
    }
}

fun testBackwardCompatibility(olderContract: Feature, newerContract: Feature): Results {
    return testBackwardCompatibilityInParallel(olderContract, newerContract, 3)
}

fun testBackwardCompatibility(olderContract: Feature, newerContract: Feature, threadCount: Int): Results {
    // USED AS A CONVENIENT SWITCH FOR TESTING, CAN BE DISPOSED OF ONCE THIS WORK IS COMPLETE
    return testBackwardCompatibilityInParallel(olderContract, newerContract, threadCount)
//    return testBackwardCompatibilitySerially(olderContract, newerContract)
}

fun testBackwardCompatibilityInParallel(olderContract: Feature, newerContract: Feature): Results {
    return testBackwardCompatibilityInParallel(olderContract, newerContract, 1)
}

fun testBackwardCompatibilityInParallel(olderContract: Feature, newerContract: Feature, threadCount: Int): Results {
    val parallelism = getParallelism(threadCount)
    println("Number of threads: $parallelism")

    val threadPool = Executors.newFixedThreadPool(parallelism)
    val dispatcher = threadPool.asCoroutineDispatcher()

    return runBlocking(dispatcher) {
        olderContract.generateBackwardCompatibilityTestScenarios().filter { !it.ignoreFailure }.map { olderScenario ->
            async(dispatcher) {
                testBackwardCompatibility(olderScenario, newerContract)
            }
        }.awaitAll()
    }.fold(Results()) { results, scenarioResults ->
        results.copy(results = results.results.plus(scenarioResults))
    }
}

private fun getParallelism(threadCount: Int?) = threadCount ?: Runtime.getRuntime().availableProcessors()

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

