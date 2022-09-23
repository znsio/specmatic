package `in`.specmatic.core.wsdl.parser.message

import `in`.specmatic.Utils.readTextResource
import `in`.specmatic.core.pattern.XMLPattern
import `in`.specmatic.core.value.FullyQualifiedName
import `in`.specmatic.core.value.toXMLNode
import `in`.specmatic.core.wsdl.parser.SOAPMessageType
import `in`.specmatic.core.wsdl.parser.TYPE_NODE_NAME
import `in`.specmatic.core.wsdl.parser.WSDL
import `in`.specmatic.core.wsdl.payload.ComplexTypedSOAPPayload
import `in`.specmatic.core.wsdl.payload.SoapPayloadType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ParseMessageStructureFromWSDLTypeTest {
    @Test
    fun `simple type generation`() {
        val wsdl = WSDL(toXMLNode(readTextResource("wsdl/stockquote.wsdl")), "")

        val parser = ParseMessageWithElementRef(wsdl, FullyQualifiedName("xsd1", "http://example.com/stockquote.xsd", "TradePriceRequest"), SOAPMessageType.Input, emptyMap(), "GetLastTradePrice")
        val parsed = parser.execute()

        val xmlType = XMLPattern("""
            <$TYPE_NODE_NAME>
                <tickerSymbol specmatic_nillable="true">(string)</tickerSymbol>
            </$TYPE_NODE_NAME>
        """.trimIndent())
        val expected = MessageTypeProcessingComplete(SoapPayloadType(mapOf("GetLastTradePrice_SOAPPayload_Input" to xmlType), ComplexTypedSOAPPayload(SOAPMessageType.Input, "TradePriceRequest", "GetLastTradePrice_SOAPPayload_Input", emptyMap())))

        assertThat(parsed).isEqualTo(expected)
    }
}