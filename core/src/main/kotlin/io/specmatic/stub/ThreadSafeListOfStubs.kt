package io.specmatic.stub

import io.specmatic.core.HttpRequest
import io.specmatic.core.KeyCheck
import io.specmatic.core.Result
import io.specmatic.core.pattern.ContractException
import io.specmatic.core.pattern.IgnoreUnexpectedKeys
import io.specmatic.mock.ScenarioStub

class ThreadSafeListOfStubs(private val httpStubs: MutableList<HttpStubData>) {
    val size: Int
        get() {
            return httpStubs.size
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
        val queueMatchResults: List<Pair<Result, HttpStubData>> = matchResults { stubs ->
            stubs.map { Pair(it.matches(httpRequest), it) }
        }

        val (_, queueMock) = queueMatchResults.findLast { (result, _) ->
            result is Result.Success
        } ?: return null

        this.remove(queueMock)
        return Pair(queueMock, queueMatchResults)
    }

    fun matchingNonTransientStub(httpRequest: HttpRequest): Pair<HttpStubData?, List<Pair<Result, HttpStubData>>> {
        val listMatchResults: List<Pair<Result, HttpStubData>> = matchResults { httpStubData ->
             httpStubData.filter { it.partial == null }.map {
                 Pair(it.matches(httpRequest), it)
             }.plus(partialMatchResults(httpStubData, httpRequest))
        }

        val mock = listMatchResults.map { (result, stubData) ->
            if (result !is Result.Success) return@map Pair(result, stubData)

            val response = if (stubData.partial == null) stubData.response
            else stubData.responsePattern.generateResponse(stubData.partial.response, stubData.resolver)

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

    private fun partialMatchResults(
        httpStubData: List<HttpStubData>,
        httpRequest: HttpRequest
    ): List<Pair<Result, HttpStubData>> {
        return httpStubData.mapNotNull { it.partial?.let { partial -> it to partial } }
            .map { (stubData, partial) ->
                val (requestPattern, _, resolver) = stubData

                val partialResolver = resolver.copy(
                    findKeyErrorCheck = KeyCheck(unexpectedKeyCheck = IgnoreUnexpectedKeys)
                )

                val partialResult = requestPattern.generate(partial.request, resolver)
                    .matches(httpRequest, partialResolver, partialResolver)

                if (!partialResult.isSuccess()) partialResult to stubData
                else Pair(stubData.matches(httpRequest), stubData)
            }
    }
}














