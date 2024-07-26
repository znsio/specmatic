package io.specmatic.core

import io.specmatic.core.utilities.exceptionCauseMessage
import io.specmatic.testStub
import org.assertj.core.api.Assertions

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
