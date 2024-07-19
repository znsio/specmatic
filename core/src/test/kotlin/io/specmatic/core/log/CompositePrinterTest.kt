package io.specmatic.core.log

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class CompositePrinterTest {
    @Test
    fun `should write to all available printers`() {
        var printed = 0

        val printer = CompositePrinter(listOf(object: LogPrinter {
                override fun print(msg: LogMessage) {
                    assertThat(msg.toLogString()).isEqualTo("Hello")
                    printed += 1
                }
            }))

        printer.print(StringLog("Hello"))
        assertThat(printed).isOne
    }
}
