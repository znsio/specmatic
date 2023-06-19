package `in`.specmatic.conversions

import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.Result
import `in`.specmatic.core.value.StringValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

internal class WsdlSpecificationTest {
    @Test
    fun `support for nillable element`() {
        val wsdlPath = "src/test/resources/wsdl/stockquote.wsdl"

        val wsdlContract = wsdlContentToFeature(File(wsdlPath).readText(), wsdlPath)

        val httpRequest = HttpRequest(
            method = "POST",
            path = "/stockquote",
            headers = mapOf("SOAPAction" to "\"http://example.com/GetLastTradePrice\""),
            body = StringValue("""<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header/><soapenv:Body><TradePriceRequest><tickerSymbol nil="true"/></TradePriceRequest></soapenv:Body></soapenv:Envelope>""")
        )

        val scenario = wsdlContract.scenarios.first()
        val result = scenario.httpRequestPattern.matches(httpRequest, scenario.resolver)

        println(result.toReport().toText())
        assertThat(result).isInstanceOf(Result.Success::class.java)
    }
}
