package run.qontract.core.wsdl.payload

import assertk.assertThat
import assertk.assertions.isEmpty
import org.junit.jupiter.api.Test

internal class EmptyHTTPBodyPayloadTest {
    @Test
    fun `generates no statements`() {
        val statements = EmptyHTTPBodyPayload().qontractStatement()
        assertThat(statements).isEmpty()
    }
}
