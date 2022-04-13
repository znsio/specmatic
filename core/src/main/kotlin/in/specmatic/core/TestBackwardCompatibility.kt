package `in`.specmatic.core

import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.utilities.capitalizeFirstChar
import `in`.specmatic.core.value.Value

fun testBackwardCompatibility(older: Feature, newerBehaviour: Feature): Results {
    return older.generateBackwardCompatibilityTestScenarios().filter { !it.ignoreFailure }.fold(Results()) { results, olderScenario ->
        val scenarioResults: List<Result> = testBackwardCompatibility(olderScenario, newerBehaviour)
        results.copy(results = results.results.plus(scenarioResults))
    }.distinct()
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

            val wholeMatchResults: List<Pair<Result, Result>> = newFeature.lookupScenariosWithDeepMatch(request).map { (scenario, result) ->
                Pair(scenario, result.updateScenario(scenario))
            }.filterNot { (_, result) ->
                result is Result.Failure && result.isFluffy()
            }.map { (newerScenario, requestResult) ->
                val newerResponsePattern = newerScenario.httpResponsePattern
                val responseResult = oldScenario.httpResponsePattern.encompasses(
                    newerResponsePattern,
                    oldScenario.resolver.copy(mismatchMessages = NewAndOldContractResponseMismatches),
                    newerScenario.resolver.copy(mismatchMessages = NewAndOldContractResponseMismatches),
                ).also {
                    it.scenario = newerScenario
                }

                if(responseResult.isFluffy())
                    null
                else
                    Pair(requestResult, responseResult)
            }.filterNotNull()

            if(wholeMatchResults.isEmpty())
                listOf(Result.Failure("""The API in the old contract does not exist in the new contract""").updateScenario(oldScenario))
            else if (wholeMatchResults.any { it.first is Result.Success && it.second is Result.Success })
                listOf(Result.Success())
            else {
                wholeMatchResults.map {
                    it.toList()
                }.flatten().filterIsInstance<Result.Failure>()
            }
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
    override fun valueMismatchFailure(expected: String, actual: Value?, mismatchMessages: MismatchMessages): Result.Failure {
        return mismatchResult(expected, actual?.type()?.typeName ?: "null", mismatchMessages)
    }

    override fun mismatchMessage(expected: String, actual: String): String {
        return "This is $expected in the new contract, $actual in the old contract"
    }

    override fun unexpectedKey(keyLabel: String, keyName: String): String {
        return "${keyLabel.lowercase().capitalizeFirstChar()} named \"$keyName\" in the request from the old contract is not in the new contract"
    }

    override fun expectedKeyWasMissing(keyLabel: String, keyName: String): String {
        return "New contract expects ${keyLabel.lowercase()} named \"$keyName\" in the request but it is missing from the old contract"
    }
}

object NewAndOldContractResponseMismatches: MismatchMessages {
    override fun mismatchMessage(expected: String, actual: String): String {
        return "This is $actual in the new contract response but $expected in the old contract"
    }

    override fun unexpectedKey(keyLabel: String, keyName: String): String {
        return "${keyLabel.lowercase().capitalizeFirstChar()} named \"$keyName\" in the response from the new contract is not in the old contract"
    }

    override fun expectedKeyWasMissing(keyLabel: String, keyName: String): String {
        return "The old contract expects ${keyLabel.lowercase()} named \"$keyName\" but it is missing in the new contract"
    }
}
