package run.qontract.test

import run.qontract.core.HttpRequest
import run.qontract.core.HttpResponse
import run.qontract.core.value.Value

interface TestExecutor {
    fun execute(request: HttpRequest): HttpResponse

    fun setServerState(serverState: Map<String, Value>)
}