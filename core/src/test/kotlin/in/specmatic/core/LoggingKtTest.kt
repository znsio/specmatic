package `in`.specmatic.core

import `in`.specmatic.core.log.logFileNameSuffix
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class LoggingKtTest {
    @Test
    fun `log filename suffix generation`() {
        assertThat(logFileNameSuffix("json", "log")).isEqualTo("-json.log")
        assertThat(logFileNameSuffix("", "log")).isEqualTo(".log")
        assertThat(logFileNameSuffix("json", "")).isEqualTo("-json")
        assertThat(logFileNameSuffix("", "")).isEqualTo("")
    }
}