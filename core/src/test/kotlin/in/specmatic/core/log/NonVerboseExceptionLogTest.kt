package `in`.specmatic.core.log

import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.value.JSONObjectValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class NonVerboseExceptionLogTest {
    val log = try {
        throw ContractException("test")
    } catch(e: Throwable) {
        NonVerboseExceptionLog(e, "msg")
    }

    @Test
    fun `non verbose exception log text`() {
        assertThat(log.toLogString().trim()).isEqualTo("msg: test")
    }

    @Test
    fun `non verbose exception log json`() {
        log.toJSONObject().apply {
            assertThat(this.getString("className")).isEqualTo("in.specmatic.core.pattern.ContractException")
            assertThat(this.getString("cause")).isEqualTo("test")
            assertThat(this.getString("message")).isEqualTo("msg")
        }
    }
}
