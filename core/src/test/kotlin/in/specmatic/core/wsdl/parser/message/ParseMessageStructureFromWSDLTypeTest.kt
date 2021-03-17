package `in`.specmatic.core.wsdl.parser.message

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.pattern.XMLPattern
import `in`.specmatic.core.value.toXMLNode
import `in`.specmatic.core.wsdl.parser.SOAPMessageType
import `in`.specmatic.core.wsdl.parser.TYPE_NODE_NAME
import `in`.specmatic.core.wsdl.parser.WSDL
import `in`.specmatic.core.wsdl.payload.ComplexTypedSOAPPayload
import `in`.specmatic.core.wsdl.payload.SoapPayloadType
import java.io.File

internal class ParseMessageStructureFromWSDLTypeTest {
    @Test
    fun `simple type generation`() {
        val wsdl = WSDL(toXMLNode(readTextResource("wsdl/stockquote.wsdl")))

        val parser = ParseMessageStructureFromWSDLType(wsdl, "xsd1:TradePriceRequest", SOAPMessageType.Input, emptyMap(), "GetLastTradePrice")
        val parsed = parser.execute()

        val xmlType = XMLPattern("""
            <$TYPE_NODE_NAME>
                <tickerSymbol>(string)</tickerSymbol>
            </$TYPE_NODE_NAME>
        """.trimIndent())
        val expected = MessageTypeProcessingComplete(SoapPayloadType(mapOf("GetLastTradePriceInput" to xmlType), ComplexTypedSOAPPayload(SOAPMessageType.Input, "TradePriceRequest", "GetLastTradePriceInput", emptyMap())))

        assertThat(parsed).isEqualTo(expected)
    }

    fun readTextResource(path: String) =
        File(
            javaClass.classLoader.getResource(path)?.file
                ?: throw ContractException("Could not find resource file $path")
        ).readText()
}