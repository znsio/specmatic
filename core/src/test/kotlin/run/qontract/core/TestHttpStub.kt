package run.qontract.core

import org.assertj.core.api.Assertions
import run.qontract.core.utilities.exceptionCauseMessage
import run.qontract.stubShouldBreak
import run.qontract.stubShouldNotBreak
import run.qontract.testStub

class TestHttpStub(private val stubRequest: HttpRequest, private val stubResponse: HttpResponse) {
    fun shouldWorkWith(contractGherkin: String) {
        val response = testStub(contractGherkin, stubRequest, stubResponse)
        Assertions.assertThat(response).isEqualTo(stubResponse)
    }

    fun shouldBreakWith(contractGherkin: String) {
        val response = try {
            testStub(contractGherkin, stubRequest, stubResponse)
        } catch(e: Throwable) {
            println(exceptionCauseMessage(e))
            return
        }

        Assertions.assertThat(response.status).isEqualTo(400)
    }
}
