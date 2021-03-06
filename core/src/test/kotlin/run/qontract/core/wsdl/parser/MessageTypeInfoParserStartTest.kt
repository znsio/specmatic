package run.qontract.core.wsdl.parser

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import run.qontract.core.value.XMLNode
import run.qontract.core.value.toXMLNode
import run.qontract.core.wsdl.payload.EmptyHTTPBodyPayload

internal class MessageTypeInfoParserStartTest {
    @Test
    fun `when there is no message node the payload should be an empty http body` () {
        val portOperationNode = mockk<XMLNode>()
        every {
            portOperationNode.findFirstChildByName("input")
        }.returns(null)
        val next = MessageTypeInfoParserStart(mockk(), portOperationNode, SOAPMessageType.Input, emptyMap(), "").execute()

        if(next is MessageTypeProcessingComplete) {
            assertThat(next.soapPayloadType?.soapPayload).isInstanceOf(EmptyHTTPBodyPayload::class.java)
        } else {
            fail("When there is no input message, the message processing should end")
        }
    }

    @Test
    fun `when there is a message node the next step should be to extract the WSDL type reference name` () {
        val portOperationNode = mockk<XMLNode>()
        val node = toXMLNode("<name/>")
        every {
            portOperationNode.findFirstChildByName("input")
        }.returns(node)
        val next = MessageTypeInfoParserStart(mockk(), portOperationNode, SOAPMessageType.Input, emptyMap(), "").execute()

        if(next is GetMessageTypeReference) {
            assertThat(next.soapPayloadType?.soapPayload).isNull()
            assertThat(next.messageTypeNode).isEqualTo(node)
        } else {
            fail("When there is input message, the next step should be to get the message type reference")
        }
    }
}