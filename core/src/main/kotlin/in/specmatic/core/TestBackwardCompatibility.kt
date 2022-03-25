package `in`.specmatic.core

import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.utilities.capitalizeFirstChar

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
                    oldScenario.resolver.copy(mismatchMessages = NewAndOldContractResponseMismatches),
                    newerScenario.resolver.copy(mismatchMessages = NewAndOldContractResponseMismatches),
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

object NewAndOldContractRequestMismatches: MismatchMessages {
    override fun mismatchMessage(expected: String, actual: String): String {
        return "New contract expected $expected, old contract sent $actual"
    }

    override fun unexpectedKey(keyLabel: String, keyName: String): String {
        return "${keyLabel.lowercase().capitalizeFirstChar()} named \"$keyName\" in the request from the old contract was unexpected by the new contract"
    }

    override fun expectedKeyWasMissing(keyLabel: String, keyName: String): String {
        return "New contract expected ${keyLabel.lowercase()} named \"$keyName\" but it was missing in the request sent from the old contract"
    }
}

object NewAndOldContractResponseMismatches: MismatchMessages {
    override fun mismatchMessage(expected: String, actual: String): String {
        return "New contract returned $actual but old contract expected $expected"
    }

    override fun unexpectedKey(keyLabel: String, keyName: String): String {
        return "${keyLabel.lowercase().capitalizeFirstChar()} named \"$keyName\" in the response from the new contract was unexpected by the old contract"
    }

    override fun expectedKeyWasMissing(keyLabel: String, keyName: String): String {
        return "The old contract expected ${keyLabel.lowercase()} named \"$keyName\" but it was missing in the response sent from the new contract"
    }
}

val BackwardCompatibilityMismatch = DefaultMismatchMessages
