package run.qontract.core

import run.qontract.core.pattern.ContractException
import java.io.File

fun testBackwardCompatibility2(older: ContractBehaviour, newerBehaviour: ContractBehaviour): Results {
    return older.generateTestScenarios().fold(Results()) { results, olderScenario ->
        newerBehaviour.setServerState(olderScenario.expectedFacts)
        var request: HttpRequest? = null

        try {
            request = olderScenario.generateHttpRequest()
            val newerScenario = newerBehaviour.lookupScenario(request)
            val newerResponsePattern = newerScenario.httpResponsePattern

            val result: Result = olderScenario.httpResponsePattern.encompasses(newerResponsePattern, olderScenario.resolver, newerScenario.resolver)

            results.copy(results = results.results.plus(Triple(result, request, null)).toMutableList())
        }
        catch(contractException: ContractException) {
            results.copy(results = results.results.plus(Triple(contractException.result(), request, null)).toMutableList())
        }
        catch(throwable: Throwable) {
            results.copy(results = results.results.plus(Triple(Result.Failure("Exception: ${throwable.localizedMessage}"), request, null)).toMutableList())
        }
    }
}

fun testBackwardCompatibility(older: ContractBehaviour, newer: ContractBehaviour): Results =
        older.generateTestScenarios().fold(Results()) { results, olderScenario ->
            newer.setServerState(olderScenario.expectedFacts)

            var request: HttpRequest? = null

            try {
                request = olderScenario.generateHttpRequest()
                val responses = newer.lookupAllResponses(request)

                val (response, result) = when {
                    responses.singleOrNull()?.headers?.get("X-Qontract-Result") == "failure" -> Pair(responses.first(), Result.Failure(responses.first().body?.displayableValue() ?: ""))
                    else -> {
                        val matchResults = responses.asSequence().map { response ->
                            Pair(response, olderScenario.matches(response))
                        }

                        matchResults.find { it.second is Result.Failure } ?: Pair(responses.first(), Result.Success())
                    }
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

data class Comparison(val older: String, val newer: String, val results: Results)

sealed class CompatibilityResult
data class TestResults(val list: Sequence<Comparison>) : CompatibilityResult()
data class JustOne(val filePath: String) : CompatibilityResult()
object NoContractsFound : CompatibilityResult()

fun testBackwardCompatibilityInDirectory(directory: File, majorVersion: Int, minorVersion: Int?): CompatibilityResult {
    val files = (directory.listFiles()?.toList() ?: emptyList()).asSequence().filterNotNull().filter {
        it.name.startsWith("$majorVersion.") && it.name.endsWith(".$CONTRACT_EXTENSION")
    }.map {
        val pieces = it.name.removeSuffix(".$CONTRACT_EXTENSION").split(".").filter { it.isNotBlank() }.map { it.toInt() }
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
        val results = testBackwardCompatibility2(ContractBehaviour(older.readText()), ContractBehaviour(newer.readText()))
        Comparison(older.absolutePath, newer.absolutePath, results)
    })
}
