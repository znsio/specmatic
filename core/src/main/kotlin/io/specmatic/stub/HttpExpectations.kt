package io.specmatic.stub

import io.specmatic.core.HttpRequest
import io.specmatic.core.Result
import io.specmatic.mock.ScenarioStub

class HttpExpectations(
    private val threadSafeHttpStubs: ThreadSafeListOfStubs = ThreadSafeListOfStubs(mutableListOf(), emptyMap()),
    private val threadSafeHttpStubQueue: ThreadSafeListOfStubs = ThreadSafeListOfStubs(mutableListOf(), emptyMap())
) {

    val stubCount: Int = threadSafeHttpStubs.size
    val transientStubCount: Int = threadSafeHttpStubQueue.size

    fun removeTransientMock(httpStubData: HttpStubData) {
        threadSafeHttpStubQueue.remove(httpStubData)
    }

    fun removeWithToken(token: String?) {
        threadSafeHttpStubQueue.removeWithToken(token)
    }

    fun addTransientStub(expectation: Pair<Result.Success, HttpStubData>, stub: ScenarioStub) {
        return threadSafeHttpStubQueue.addToStub(expectation, stub)
    }

    fun addStub(expectation: Pair<Result.Success, HttpStubData>, stub: ScenarioStub) {
        return threadSafeHttpStubs.addToStub(expectation, stub)
    }

    fun associatedTo(port: Int, defaultPort: Int): HttpExpectations {
        return HttpExpectations(threadSafeHttpStubs.stubAssociatedTo(port, defaultPort), threadSafeHttpStubQueue.stubAssociatedTo(port, defaultPort))
    }

    fun matchingStub(httpRequest: HttpRequest): Pair<HttpStubData?, List<Pair<Result, HttpStubData>>> {
        return threadSafeHttpStubQueue.matchingTransientStub(httpRequest)
            ?: threadSafeHttpStubs.matchingNonTransientStub(httpRequest)
    }
}
