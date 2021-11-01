package `in`.specmatic.core.log

import application.captureStandardOutput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ConsolePrinterTest {
    @Test
    fun `should print to standard output`() {
        val output: Pair<String, Unit> = captureStandardOutput {
            ConsolePrinter.print(StringLog("hello"))
        }

        assertThat(output.first).isEqualTo("hello")
    }
}