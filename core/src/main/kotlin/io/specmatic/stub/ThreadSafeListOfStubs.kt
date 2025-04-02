package io.specmatic.stub

import io.specmatic.core.HttpRequest
import io.specmatic.core.KeyCheck
import io.specmatic.core.Result
import io.specmatic.core.invalidRequestStatuses
import io.specmatic.core.pattern.ContractException
import io.specmatic.core.pattern.IgnoreUnexpectedKeys
import io.specmatic.mock.ScenarioStub

private const val PARTIAL = "partial"
private const val EXACT = "exact"

class ThreadSafeListOfStubs(
    private val httpStubs: MutableList<HttpStubData>,
    private val specToPortMap: Map<String, Int>
) {
    val size: Int
        get() {
            return httpStubs.size
        }

    fun stubAssociatedTo(port: Int, defaultPort: Int): ThreadSafeListOfStubs {
        return portToListOfStubsMap(defaultPort)[port] ?: emptyStubs()
    }

    fun matchResults(fn: (List<HttpStubData>) -> List<Pair<Result, HttpStubData>>): List<Pair<Result, HttpStubData>> {
        synchronized(this) {
            return fn(httpStubs.toList())
        }
    }

    fun addToStub(result: Pair<Result, HttpStubData?>, stub: ScenarioStub) {
        synchronized(this) {
            result.second.let {
                if(it != null)
                    httpStubs.add(0, it.copy(delayInMilliseconds = stub.delayInMilliseconds, stubToken = stub.stubToken))
            }
        }
    }

    fun remove(element: HttpStubData) {
        synchronized(this) {
            httpStubs.remove(element)
        }
    }

    fun removeWithToken(token: String?) {
        synchronized(this) {
            httpStubs.mapIndexed { index, httpStubData ->
                if (httpStubData.stubToken == token) index else null
            }.filterNotNull().reversed().map { index ->
                httpStubs.removeAt(index)
            }
        }
    }

    fun matchingTransientStub(httpRequest: HttpRequest): Pair<HttpStubData, List<Pair<Result, HttpStubData>>>? {
        val expectedResponseCode = httpRequest.expectedResponseCode()

        val queueMatchResults: List<Pair<Result, HttpStubData>> = matchResults { stubs ->
            stubs.filter {
                hasExpectedResponseCode(it, expectedResponseCode)
            }.map {
                Pair(it.matches(httpRequest), it)
            }
        }

        val (_, queueMock) = queueMatchResults.findLast { (result, _) ->
            result is Result.Success
        } ?: return null

        return Pair(queueMock, queueMatchResults)
    }

    private fun hasExpectedResponseCode(httpStubData: HttpStubData, expectedResponseCode: Int?): Boolean {
        expectedResponseCode ?: return true
        return httpStubData.responsePattern.status == expectedResponseCode
    }

    fun matchingNonTransientStaticStub(httpRequest: HttpRequest): Pair<HttpStubData?, List<Pair<Result, HttpStubData>>> {
        val expectedResponseCode = httpRequest.expectedResponseCode()

        val listMatchResults: List<Pair<Result, HttpStubData>> = matchResults { httpStubData ->
            httpStubData.filter {
                hasExpectedResponseCode(it, expectedResponseCode)
            }.filter { it.partial == null }.map {
                Pair(it.matches(httpRequest), it)
            }.plus(partialMatchResults(httpStubData, httpRequest))
        }

        val mocks = listMatchResults.map { (result, stubData) ->
            if (result !is Result.Success) return@map Pair(result, stubData)

            val response =
                if (stubData.partial == null)
                    stubData.response
                else
                    stubData.responsePattern.fillInTheBlanks(stubData.partial.response, stubData.resolver)

            val stubResponse = HttpStubResponse(
                response,
                stubData.delayInMilliseconds,
                stubData.contractPath,
                feature = stubData.feature,
                scenario = stubData.scenario
            )

            try {
                val originalRequest =
                    if (stubData.partial != null) stubData.partial.request
                    else stubData.originalRequest

                stubResponse.resolveSubstitutions(
                    httpRequest,
                    originalRequest ?: httpRequest,
                    stubData.data
                )

                result to stubData.copy(response = response)
            } catch (e: ContractException) {
                if (isMissingData(e)) Pair(e.failure(), stubData)
                else throw e
            }
        }

        val successfulMatches = mocks.filter { (result, _) -> result is Result.Success }
        val grouped = successfulMatches.groupBy { (_, stubData) ->
            if(stubData.partial != null) PARTIAL else EXACT
        }

        val exactMatch = grouped[EXACT].orEmpty().sortedBy {
            it.second.originalRequest?.precisionScore ?: Int.MAX_VALUE
        }.find { (result, _) -> result is Result.Success }

        if(exactMatch != null)
            return Pair(exactMatch.second, listMatchResults)

        val partialMatch = grouped[PARTIAL].orEmpty().sortedBy {
            it.second.originalRequest?.precisionScore ?: Int.MAX_VALUE
        }.find { (result, _) -> result is Result.Success }

        if(partialMatch != null)
            return Pair(partialMatch.second, listMatchResults)

        return Pair(null, listMatchResults)
    }

    fun matchingNonTransientDynamicStub(httpRequest: HttpRequest): Pair<HttpStubData?, List<Pair<Result, HttpStubData>>> {
        val expectedResponseCode = httpRequest.expectedResponseCode()

        val listMatchResults: List<Pair<Result, HttpStubData>> = matchResults { httpStubData ->
             httpStubData.filter {
                 hasExpectedResponseCode(it, expectedResponseCode)
             }.filter { it.partial == null }.map {
                 Pair(it.matches(httpRequest), it)
             }.plus(partialMatchResults(httpStubData, httpRequest))
        }

        val mock = listMatchResults.map { (result, stubData) ->
            if (result !is Result.Success) return@map Pair(result, stubData)

            val response = if (stubData.partial == null) stubData.response
            else stubData.responsePattern.fillInTheBlanks(stubData.partial.response, stubData.resolver)

            val stubResponse = HttpStubResponse(
                response,
                stubData.delayInMilliseconds,
                stubData.contractPath,
                feature = stubData.feature,
                scenario = stubData.scenario
            )

            try {
                val originalRequest =
                    if (stubData.partial != null) stubData.partial.request
                    else stubData.originalRequest

                stubResponse.resolveSubstitutions(
                    httpRequest,
                    originalRequest ?: httpRequest,
                    stubData.data
                )

                result to stubData.copy(response = response)
            } catch (e: ContractException) {
                if (isMissingData(e)) Pair(e.failure(), stubData)
                else throw e
            }
        }.find { (result, _) -> result is Result.Success }

        return Pair(mock?.second, listMatchResults)
    }

    private fun portToListOfStubsMap(defaultPort: Int): Map<Int, ThreadSafeListOfStubs> {
        synchronized(this) {
            return httpStubs.groupBy {
                specToPortMap[it.contractPath] ?: defaultPort
            }.mapValues { (_, stubs) ->
                ThreadSafeListOfStubs(stubs as MutableList<HttpStubData>, specToPortMap)
            }
        }
    }

    private fun partialMatchResults(
        httpStubData: List<HttpStubData>,
        httpRequest: HttpRequest
    ): List<Pair<Result, HttpStubData>> {
        val expectedResponseCode = httpRequest.expectedResponseCode()

        return httpStubData.mapNotNull { it.partial?.let { partial -> it to partial } }
            .filter {
                hasExpectedResponseCode(it.first, expectedResponseCode)
            }.map { (stubData, partial) ->
                val (requestPattern, _, resolver) = stubData

                val partialResolver = resolver.copy(
                    findKeyErrorCheck = KeyCheck(unexpectedKeyCheck = IgnoreUnexpectedKeys)
                )

                val partialResult = requestPattern.generate(partial.request, resolver)
                    .matches(httpRequest, partialResolver, partialResolver)

                if (!partialResult.isSuccess()) return@map partialResult to stubData
                if (partial.response.status in invalidRequestStatuses) return@map partialResult to stubData
                Pair(stubData.matches(httpRequest), stubData)
            }
    }

    companion object {
        fun emptyStubs(): ThreadSafeListOfStubs {
            return ThreadSafeListOfStubs(mutableListOf(), emptyMap())
        }
    }
}














