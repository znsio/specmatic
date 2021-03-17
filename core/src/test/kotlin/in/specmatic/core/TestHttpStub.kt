package `in`.specmatic.core

import org.assertj.core.api.Assertions
import `in`.specmatic.core.utilities.exceptionCauseMessage
import `in`.specmatic.stubShouldBreak
import `in`.specmatic.stubShouldNotBreak
import `in`.specmatic.testStub

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
