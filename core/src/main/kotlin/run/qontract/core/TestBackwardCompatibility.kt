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

sealed class TestResult
data class Comparison(val older: String, val newer: String, val results: Results)
data class ResultList(val list: Sequence<Comparison>) : TestResult()
data class JustOne(val file: String) : TestResult()
object Nothing : TestResult()

fun testBackwardCompatibilityInDirectory(directory: File, version: Int): TestResult {
    val files = (directory.listFiles()?.toList() ?: emptyList()).asSequence().filterNotNull().filter {
        it.name.startsWith("$version.") && it.name.endsWith(".$CONTRACT_EXTENSION")
    }.map {
        val pieces = it.name.removeSuffix(".$CONTRACT_EXTENSION").split(".").filter { it.isNotBlank() }.map { it.toInt() }
        Triple(it, pieces.first(), pieces.getOrElse(1) { 0 })
    }.sortedWith(compareBy({it.second}, {it.third}) ).map { it.first }.toList()

    if(files.elementAtOrNull(0) == null) {
        return Nothing
    }

    if(files.elementAtOrNull(1) == null)
        return JustOne(files.first().path)

    return ResultList(files.zipWithNext().asSequence().map { (older, newer) ->
        val results = testBackwardCompatibility(ContractBehaviour(older.readText()), ContractBehaviour(newer.readText()))
        Comparison(older.absolutePath, newer.absolutePath, results)
    })
}
