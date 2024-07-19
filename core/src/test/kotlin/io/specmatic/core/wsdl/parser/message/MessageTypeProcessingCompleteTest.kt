package io.specmatic.core.wsdl.parser.message

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class MessageTypeProcessingCompleteTest {
    @Test
    fun `this class in the end of the line for message type processing`() {
        val parser = MessageTypeProcessingComplete(null)
        assertThat(parser.execute()).isEqualTo(parser)
    }
}