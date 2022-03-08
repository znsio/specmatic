package `in`.specmatic.core

import `in`.specmatic.core.pattern.ContractException
import kotlinx.coroutines.*
import java.util.concurrent.Executors

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
                    oldScenario.resolver.copy(mismatchMessages = BackwardCompatibilityMismatch),
                    newerScenario.resolver.copy(mismatchMessages = BackwardCompatibilityMismatch),
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

object BackwardCompatibilityMismatch: MismatchMessages {
    override fun mismatchMessage(expected: String, actual: String): String {
        return "Older contract expected $expected but got $actual"
    }
}
