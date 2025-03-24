package io.specmatic.stub

import io.specmatic.core.HttpRequest
import io.specmatic.core.KeyCheck
import io.specmatic.core.Result
import io.specmatic.core.invalidRequestStatuses
import io.specmatic.core.pattern.ContractException
import io.specmatic.core.pattern.IgnoreUnexpectedKeys
import io.specmatic.mock.ScenarioStub

enum class StubCategory {
    TRANSIENT,
    PERSISTENT
}

enum class StubType {
    NORMAL,
    DATA_LOOKUP,
    PARTIAL
}

data class ClassifiedStub(val stub: HttpStubData, val category: StubCategory, val type: StubType): Comparable<ClassifiedStub> {
    companion object {
        fun from(httpStubData: HttpStubData): ClassifiedStub {
            val type = when {
                httpStubData.data.jsonObject.isNotEmpty() -> StubType.DATA_LOOKUP
                httpStubData.partial != null -> StubType.PARTIAL
                else -> StubType.NORMAL
            }

            val category = when(httpStubData.stubToken != null) {
                true -> StubCategory.TRANSIENT
                false -> StubCategory.PERSISTENT
            }

            return ClassifiedStub(httpStubData, category, type)
        }
    }

    override fun compareTo(other: ClassifiedStub): Int {
        return compareValuesBy(this, other, { it.category }, { it.type })
    }
}

class ThreadSafeListOfStubs(httpStubs: List<HttpStubData>, private val specToPortMap: Map<String, Int>) {

    private val httpStubs: MutableList<ClassifiedStub> = httpStubs.map(ClassifiedStub::from).sortedWith(compareBy({ it.category }, { it.type })).toMutableList()

    val size: Int get() = synchronized(this) { httpStubs.size }

    fun stubAssociatedTo(port: Int, defaultPort: Int): ThreadSafeListOfStubs {
        return portToListOfStubsMap(defaultPort)[port] ?: emptyStubs()
    }

    fun matchResults(fn: (List<HttpStubData>) -> List<Pair<Result, HttpStubData>>): List<Pair<Result, HttpStubData>> {
        return synchronized(this) {
            fn(httpStubs.map { it.stub })
        }
    }

    fun addToStub(result: Pair<Result, HttpStubData?>, stub: ScenarioStub) {
        synchronized(this) {
            result.second?.let { httpStubData ->
                val modifiedHttpStubData = httpStubData.copy(
                    delayInMilliseconds = stub.delayInMilliseconds,
                    stubToken = stub.stubToken
                )
                val newClassifiedStub = ClassifiedStub.from(modifiedHttpStubData)
                addStubData(newClassifiedStub)
            }
        }
    }

    private fun addStubData(newClassifiedStub: ClassifiedStub) = synchronized(this) {
        val index = httpStubs.indexOfFirst { it >= newClassifiedStub }.takeIf { it >= 0 } ?: httpStubs.size
        httpStubs.add(index, newClassifiedStub)
    }

    fun remove(element: HttpStubData) {
        synchronized(this) {
            httpStubs.removeAll { it.stub == element }
        }
    }

    fun removeWithToken(token: String?) {
        synchronized(this) {
            httpStubs.removeAll { it.stub.stubToken == token }
        }
    }

    fun matchingTransientStub(httpRequest: HttpRequest): Pair<HttpStubData, List<Pair<Result, HttpStubData>>>? {
        val expectedResponseCode = httpRequest.expectedResponseCode()

        val queueMatchResults: List<Pair<Result, HttpStubData>> = matchResults { stubs ->
            stubs.filter {
                hasExpectedResponseCode(it, expectedResponseCode)
            }.map { Pair(it.matches(httpRequest), it) }
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

    fun matchingNonTransientStub(httpRequest: HttpRequest): Pair<HttpStubData?, List<Pair<Result, HttpStubData>>> {
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

            val originalRequest = stubData.partial?.request ?: stubData.originalRequest
            val stubResponse = HttpStubResponse(
                response = stubData.partial?.response ?: stubData.response,
                stubData.delayInMilliseconds,
                stubData.contractPath,
                feature = stubData.feature,
                scenario = stubData.scenario
            )

            try {
                val resolvedResponse = stubResponse.resolveSubstitutions(
                    request = httpRequest,
                    originalRequest = originalRequest ?: httpRequest,
                    data = stubData.data
                ).response

                val response = if (stubData.partial == null) resolvedResponse
                else stubData.responsePattern.fillInTheBlanks(resolvedResponse, stubData.resolver)

                result to stubData.copy(response = response)
            } catch (e: ContractException) {
                if (isMissingData(e)) Pair(e.failure(), stubData)
                else throw e
            }
        }.find { (result, _) -> result is Result.Success }

        return Pair(mock?.second, listMatchResults)
    }

    private fun portToListOfStubsMap(defaultPort: Int): Map<Int, ThreadSafeListOfStubs> {
        return synchronized(this) {
            httpStubs.groupBy {
                specToPortMap[it.stub.contractPath] ?: defaultPort
            }.mapValues { (_, stubs) ->
                ThreadSafeListOfStubs(stubs.map { it.stub }, specToPortMap)
            }
        }
    }

    private fun partialMatchResults(httpStubData: List<HttpStubData>, httpRequest: HttpRequest): List<Pair<Result, HttpStubData>> {
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
