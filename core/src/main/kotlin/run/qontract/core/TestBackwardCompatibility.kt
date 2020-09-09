package run.qontract.core

import run.qontract.core.pattern.ContractException
import java.io.File

fun testBackwardCompatibility(older: Feature, newerBehaviour: Feature): Results {
    return older.generateTestScenarios().filter { !it.ignoreFailure }.fold(Results()) { results, olderScenario ->
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

                if(scenarioResults.any { it is Result.Success }) {
                    results.copy(results = results.results.plus(Result.Success()).toMutableList())
                } else {
                    results.copy(results = results.results.plus(scenarioResults).toMutableList())
                }
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

data class Comparison(val older: String, val newer: String, val results: Results)

sealed class CompatibilityResult
data class TestResults(val list: Sequence<Comparison>) : CompatibilityResult()
data class JustOne(val filePath: String) : CompatibilityResult()
object NoContractsFound : CompatibilityResult()

fun testBackwardCompatibilityInDirectory(directory: File, majorVersion: Int, minorVersion: Int?): CompatibilityResult {
    val files = (directory.listFiles()?.toList() ?: emptyList()).asSequence().filterNotNull().filter {
        it.name.startsWith("$majorVersion.") && it.name.endsWith(".$QONTRACT_EXTENSION")
    }.map {
        val pieces = it.name.removeSuffix(".$QONTRACT_EXTENSION").split(".").filter { it.isNotBlank() }.map { it.toInt() }
        Triple(it, pieces.first(), pieces.getOrElse(1) { 0 })
    }.sortedWith(compareBy({it.second}, {it.third}) ).filter { (_, _, fileMinorVersion) ->
        minorVersion == null || fileMinorVersion <= minorVersion
    }.map { it.first }

    if(files.elementAtOrNull(0) == null) {
        return NoContractsFound
    }

    if(files.elementAtOrNull(1) == null)
        return JustOne(files.first().path)

    return TestResults(files.zipWithNext().asSequence().map { (older, newer) ->
        val results = testBackwardCompatibility(Feature(older.readText()), Feature(newer.readText()))
        Comparison(older.absolutePath, newer.absolutePath, results)
    })
}
