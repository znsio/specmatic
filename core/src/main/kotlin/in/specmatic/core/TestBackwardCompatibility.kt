package `in`.specmatic.core

import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.utilities.capitalizeFirstChar
import `in`.specmatic.core.value.NullValue
import `in`.specmatic.core.value.Value

fun testBackwardCompatibility(older: Feature, newerBehaviour: Feature): Results {
    return older.generateBackwardCompatibilityTestScenarios().filter { !it.ignoreFailure }.fold(Results()) { results, olderScenario ->
        val scenarioResults: List<Result> = testBackwardCompatibility(olderScenario, newerBehaviour)
        results.copy(results = results.results.plus(scenarioResults))
    }.distinct()
}

fun findDifferences(older: Feature, newerBehaviour: Feature): Results {
    return older.generateBackwardCompatibilityTestScenarios().filter { !it.ignoreFailure }.fold(Results()) { results, olderScenario ->
        val scenarioResults: List<Result> = findDifferences(olderScenario, newerBehaviour)
        results.copy(results = results.results.plus(scenarioResults))
    }.distinct()
}

class BackwardCompatibilityTest(private val olderScenario: Scenario, private val newerContract: Feature) {
    val name: String
        get() {
            return olderScenario.name
        }

    fun execute(): List<Result> {
        return testBackwardCompatibility(olderScenario, newerContract)
    }
}

fun generateBackwardCompatibilityTests(older: Feature, newerContract: Feature): List<BackwardCompatibilityTest> {
    return older.generateBackwardCompatibilityTestScenarios().filter { !it.ignoreFailure }.map { olderScenario ->
        BackwardCompatibilityTest(olderScenario, newerContract)
    }
}

fun testBackwardCompatibility(
    oldScenario: Scenario,
    newFeature_: Feature
): List<Result> {
    val newFeature = newFeature_.copy()

    newFeature.setServerState(oldScenario.expectedFacts)

    return try {
        val request = oldScenario.generateHttpRequest()

        val wholeMatchResults: List<Pair<Result, Result>> =
            newFeature.compatibilityLookup(request).map { (scenario, result) ->
                Pair(scenario, result.updateScenario(scenario))
            }.filterNot { (_, result) ->
                result is Result.Failure && result.isFluffy()
            }.mapNotNull { (newerScenario, requestResult) ->
                val newerResponsePattern = newerScenario.httpResponsePattern
                val responseResult = oldScenario.httpResponsePattern.encompasses(
                    newerResponsePattern,
                    oldScenario.resolver.copy(mismatchMessages = NewAndOldContractResponseMismatches),
                    newerScenario.resolver.copy(mismatchMessages = NewAndOldContractResponseMismatches),
                ).also {
                    it.scenario = newerScenario
                }

                if (responseResult.isFluffy())
                    null
                else
                    Pair(requestResult, responseResult)
            }

        if(wholeMatchResults.isEmpty())
            listOf(Result.Failure("""This API exists in the old contract but not in the new contract""").updateScenario(oldScenario))
        else if (wholeMatchResults.any { it.first is Result.Success && it.second is Result.Success })
            listOf(Result.Success())
        else {
            wholeMatchResults.map {
                it.toList()
            }.flatten().filterIsInstance<Result.Failure>()
        }
    } catch(emptyContract: EmptyContract) {
        val atThisFilePath = if(newFeature.path.isNotEmpty()) " at ${newFeature.path}" else ""
        listOf(Result.Failure("The contract$atThisFilePath had no operations"))
    }
    catch (contractException: ContractException) {
        listOf(contractException.failure())
    } catch (stackOverFlowException: StackOverflowError) {
        listOf(Result.Failure("Exception: Stack overflow error, most likely caused by a recursive definition. Please report this with a sample contract as a bug!"))
    } catch (throwable: Throwable) {
        listOf(Result.Failure("Exception: ${throwable.localizedMessage}"))
    }
}

fun findDifferences(
    oldScenario: Scenario,
    newFeature_: Feature
): List<Result> {
    val newFeature = newFeature_.copy()

    newFeature.setServerState(oldScenario.expectedFacts)

    return try {
        val request = oldScenario.generateHttpRequest()

        val wholeMatchResults: List<Pair<Result, Result>> =
            newFeature.compatibilityLookup(request).map { (scenario, result) ->
                Pair(scenario, result.updateScenario(scenario))
            }.filterNot { (_, result) ->
                result is Result.Failure && result.isFluffy()
            }.mapNotNull { (newerScenario, requestResult) ->
                val newerResponsePattern = newerScenario.httpResponsePattern
                val newerResponse = newerResponsePattern.generateResponseWithAll(newerScenario.resolver)

                val responseResult = oldScenario.httpResponsePattern.matches(newerResponse, oldScenario.resolver.copy(mismatchMessages = ContractResponseComparisonMismatches))

                if (responseResult.isFluffy())
                    null
                else
                    Pair(requestResult, responseResult)
            }

        if(wholeMatchResults.isEmpty())
            listOf(Result.Failure("""This API exists in the old contract but not in the new contract""").updateScenario(oldScenario))
        else if (wholeMatchResults.any { it.first is Result.Success && it.second is Result.Success })
            listOf(Result.Success())
        else {
            wholeMatchResults.map {
                val failures: List<Result.Failure> = it.toList().filterIsInstance<Result.Failure>()
                Result.fromFailures(failures).updateScenario(oldScenario)
            }
        }
    } catch(emptyContract: EmptyContract) {
        val atThisFilePath = if(newFeature.path.isNotEmpty()) " at ${newFeature.path}" else ""
        listOf(Result.Failure("The contract$atThisFilePath had no operations"))
    } catch (contractException: ContractException) {
        listOf(contractException.failure())
    } catch (stackOverFlowException: StackOverflowError) {
        listOf(Result.Failure("Exception: Stack overflow error, most likely caused by a recursive definition. Please report this with a sample contract as a bug!"))
    } catch (throwable: Throwable) {
        listOf(Result.Failure("Exception: ${throwable.localizedMessage}"))
    }
}

object ContractResponseComparisonMismatches : MismatchMessages {
    override fun valueMismatchFailure(expected: String, actual: Value?, mismatchMessages: MismatchMessages): Result.Failure {
        return mismatchResult(expected, nullOrValue(actual), mismatchMessages)
    }

    private fun nullOrValue(actual: Value?): String {
        return when (actual) {
            is NullValue -> "nullable"
            else -> actual?.type()?.typeName ?: "null"
        }
    }

    override fun mismatchMessage(expected: String, actual: String): String {
        return "  This is $expected in the old contract, $actual in the new contract."
    }

    override fun unexpectedKey(keyLabel: String, keyName: String): String {
        return "  The newer contract didn't have $keyLabel $keyName."
    }

    override fun expectedKeyWasMissing(keyLabel: String, keyName: String): String {
        return "  The older contract had $keyLabel $keyName, which is missing in the new contract."
    }

}

object NewAndOldContractRequestMismatches: MismatchMessages {
    override fun valueMismatchFailure(expected: String, actual: Value?, mismatchMessages: MismatchMessages): Result.Failure {
        return mismatchResult(expected, nullOrValue(actual), mismatchMessages)
    }

    private fun nullOrValue(actual: Value?): String {
        return when (actual) {
            is NullValue -> "nullable"
            else -> actual?.type()?.typeName ?: "null"
        }
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
