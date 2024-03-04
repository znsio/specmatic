package `in`.specmatic.test

import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.HttpResponse
import `in`.specmatic.core.value.Value

interface TestExecutor {
    fun execute(request: HttpRequest): HttpResponse

    fun setServerState(serverState: Map<String, Value>) {

    }
}