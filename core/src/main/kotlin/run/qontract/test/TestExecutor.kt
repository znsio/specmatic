package run.qontract.test

import run.qontract.core.HttpRequest
import run.qontract.core.HttpResponse
import kotlin.jvm.Throws
import java.io.IOException
import java.net.URISyntaxException
import java.net.MalformedURLException
import run.qontract.core.ServerSetupStateException

interface TestExecutor {
    @Throws(IOException::class, URISyntaxException::class)
    fun execute(request: HttpRequest): HttpResponse

    @Throws(MalformedURLException::class, URISyntaxException::class, ServerSetupStateException::class)
    fun setServerState(serverState: Map<String, Any?>)
}