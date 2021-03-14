package run.qontract.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ResultKtTest {
    @Test
    fun `add response body to result variables`() {
        val result = Result.Success().withBindings(mapOf("data" to "response-body"), HttpResponse.OK("10")) as Result.Success
        assertThat(result.variables).isEqualTo(mapOf("data" to "10"))
    }
}