package run.qontract.core.wsdl.parser.message

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.pattern.ContractException
import run.qontract.core.pattern.XMLPattern
import run.qontract.core.value.toXMLNode
import run.qontract.core.wsdl.parser.SOAPMessageType
import run.qontract.core.wsdl.parser.TYPE_NODE_NAME
import run.qontract.core.wsdl.parser.WSDL
import run.qontract.core.wsdl.payload.ComplexTypedSOAPPayload
import run.qontract.core.wsdl.payload.SoapPayloadType
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