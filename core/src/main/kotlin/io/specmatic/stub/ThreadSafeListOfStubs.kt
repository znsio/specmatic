package io.specmatic.stub

import io.specmatic.core.Result
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
}