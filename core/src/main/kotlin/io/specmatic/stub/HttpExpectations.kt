package io.specmatic.stub

import io.specmatic.core.HttpRequest
import io.specmatic.core.Result
import io.specmatic.mock.ScenarioStub

class HttpExpectations(
    private val nonTransientStatic: ThreadSafeListOfStubs = ThreadSafeListOfStubs(mutableListOf(), emptyMap()),
    private val transientStatic: ThreadSafeListOfStubs = ThreadSafeListOfStubs(mutableListOf(), emptyMap()),
    private val nonTransientDynamic: ThreadSafeListOfStubs = ThreadSafeListOfStubs(mutableListOf(), emptyMap())
) {

    val stubCount: Int = nonTransientStatic.size
    val transientStubCount: Int = transientStatic.size

    fun removeTransientMock(httpStubData: HttpStubData) {
        transientStatic.remove(httpStubData)
    }

    fun removeWithToken(token: String?) {
        transientStatic.removeWithToken(token)
    }

    fun addDynamicTransient(expectation: Pair<Result.Success, HttpStubData>, stub: ScenarioStub) {
        return transientStatic.addToStub(expectation, stub)
    }

    fun addDynamic(expectation: Pair<Result.Success, HttpStubData>, stub: ScenarioStub) {
        return nonTransientDynamic.addToStub(expectation, stub)
    }

    fun associatedTo(port: Int, defaultPort: Int): HttpExpectations {
        return HttpExpectations(nonTransientStatic.stubAssociatedTo(port, defaultPort), transientStatic.stubAssociatedTo(port, defaultPort), nonTransientDynamic)
    }

    fun matchingStub(httpRequest: HttpRequest): Pair<HttpStubData?, List<Pair<Result, HttpStubData>>> {
        val transientMatch = transientStatic.matchingTransientStub(httpRequest)
        if(transientMatch != null)
            return transientMatch

        val nonTransientDynamicMatch = nonTransientDynamic.matchingNonTransientStub(httpRequest)
        if(nonTransientDynamicMatch.first != null)
            return nonTransientDynamicMatch

        val nonTransientStaticMatch = nonTransientStatic.matchingNonTransientStub(httpRequest)
        if(nonTransientStaticMatch.first != null)
            return nonTransientStaticMatch

        val failures = nonTransientDynamicMatch.second + nonTransientStaticMatch.second

        return Pair(null, failures)
    }
}
