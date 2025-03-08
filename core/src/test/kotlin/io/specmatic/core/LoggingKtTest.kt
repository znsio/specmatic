package io.specmatic.core

import io.specmatic.core.log.InfoLogger
import io.specmatic.core.log.logFileNameSuffix
import io.specmatic.stub.captureStandardOutput
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

    @Test
    fun `log with no indentation`() {
        val (output, _) = captureStandardOutput(trim = false) {
            InfoLogger.log("Hello")
        }

        assertThat(output.trimEnd()).isEqualTo("Hello")
    }

    @Test
    fun `log with indentation`() {
        val (output, _) = captureStandardOutput(trim = false) {
            InfoLogger.withIndentation(2) {
                InfoLogger.log("Hello")
            }
        }

        assertThat(output.trimEnd()).isEqualTo(" ".repeat(2) + "Hello")
    }

    @Test
    fun `log with nested indentation`() {
        val (output, _) = captureStandardOutput(trim = false) {
            InfoLogger.log("Outer")
            InfoLogger.withIndentation(2) {
                InfoLogger.log("Inner")
                InfoLogger.withIndentation(2) {
                    InfoLogger.log("Innermost")
                }
                InfoLogger.log("Return to inner")
            }
            InfoLogger.log("Return to outer")
        }

        assertThat(output.trimEnd()).isEqualTo("Outer${System.lineSeparator()}  Inner${System.lineSeparator()}    Innermost${System.lineSeparator()}  Return to inner${System.lineSeparator()}Return to outer")
    }
}