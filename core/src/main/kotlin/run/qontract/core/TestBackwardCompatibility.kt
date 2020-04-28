package run.qontract.core

import run.qontract.core.pattern.ContractException
import java.io.File

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

data class Comparison(val older: String, val newer: String, val results: Results)

sealed class CompatibilityResult
data class TestResults(val list: Sequence<Comparison>) : CompatibilityResult()
data class JustOne(val file: String) : CompatibilityResult()
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
        val results = testBackwardCompatibility(ContractBehaviour(older.readText()), ContractBehaviour(newer.readText()))
        Comparison(older.absolutePath, newer.absolutePath, results)
    })
}
