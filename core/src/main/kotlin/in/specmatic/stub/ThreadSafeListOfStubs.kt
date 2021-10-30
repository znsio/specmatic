package `in`.specmatic.stub

import `in`.specmatic.core.Result
import `in`.specmatic.mock.ScenarioStub

class ThreadSafeListOfStubs(private val httpStubs: MutableList<HttpStubData>) {
    fun matchResults(fn: (List<HttpStubData>) -> List<Pair<Result, HttpStubData>>): List<Pair<Result, HttpStubData>> {
        synchronized(this) {
            return fn(httpStubs.toList())
        }
    }

    fun addToStub(result: Pair<Result, HttpStubData?>, stub: ScenarioStub) {
        synchronized(this) {
            result.second.let {
                if(it != null)
                    httpStubs.add(0, it.copy(delayInSeconds = stub.delayInSeconds))
            }
        }
    }
}