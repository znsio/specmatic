package `in`.specmatic.core.log

import `in`.specmatic.core.value.JSONObjectValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class NewLineLogMessageTest {
    @Test
    fun `returns a newline string`() {
        assertThat(NewLineLogMessage.toLogString()).isEqualTo("\n")
    }

    @Test
    fun `returns an empty JSON object`() {
        assertThat(NewLineLogMessage.toJSONObject()).isEqualTo(JSONObjectValue())
    }
}