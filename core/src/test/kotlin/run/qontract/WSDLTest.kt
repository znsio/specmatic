package run.qontract

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.Feature
import run.qontract.core.value.toXMLNode
import run.qontract.core.wsdl.WSDL

class WSDLTest {
    @Test
    fun `happy path WSDL conversion`() {
        val wsdlContent = """
            <definitions name="StockQuote"
                         targetNamespace="http://example.com/stockquote.wsdl"
                         xmlns:tns="http://www.w3.org/2001/XMLSchema"
                         xmlns:xsd1="http://example.com/stockquote.xsd"
                         xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
                         xmlns="http://schemas.xmlsoap.org/wsdl/">

              <types>
                <schema targetNamespace="http://example.com/stockquote.xsd"
                        xmlns="http://www.w3.org/2000/10/XMLSchema">
                  <element name="TradePriceRequest">
                    <complexType>
                      <all>
                        <element name="tickerSymbol" type="string"/>
                      </all>
                    </complexType>
                  </element>
                  <element name="TradePrice">
                     <complexType>
                       <all>
                         <element name="price" type="float"/>
                       </all>
                     </complexType>
                  </element>
                </schema>
              </types>

              <message name="GetLastTradePriceInput">
                <part name="body" element="xsd1:TradePriceRequest"/>
              </message>

              <message name="GetLastTradePriceOutput">
                <part name="body" element="xsd1:TradePrice"/>
              </message>

              <portType name="StockQuotePortType">
                <operation name="GetLastTradePrice">
                  <input message="tns:GetLastTradePriceInput"/>
                  <output message="tns:GetLastTradePriceOutput"/>
                </operation>
              </portType>

              <binding name="StockQuoteSoapBinding" type="tns:StockQuotePortType">
                <soap:binding style="document" transport="http://schemas.xmlsoap.org/soap/http"/>
                <operation name="GetLastTradePrice">
                  <soap:operation soapAction="http://example.com/GetLastTradePrice"/>
                  <input>
                    <soap:body use="literal"/>
                  </input>
                  <output>
                    <soap:body use="literal"/>
                  </output>
                </operation>
              </binding>

              <service name="StockQuoteService">
                <documentation>My first service</documentation>
                <port name="StockQuotePort" binding="tns:StockQuoteSoapBinding">
                  <soap:address location="http://example.com/stockquote"/>
                </port>
              </service>

            </definitions>
        """.trimIndent()

        val wsdl = WSDL(toXMLNode(wsdlContent))
        val generatedGherkin: String = wsdl.convertToGherkin()
        val expectedGherkin = """
            Feature: StockQuoteService

                Scenario: GetLastTradePrice
                    Given type GetLastTradePriceInput
                    ""${'"'}
                    <TradePriceRequest>
                        <tickerSymbol>(string)</tickerSymbol>
                    </TradePriceRequest>
                    ""${'"'}
                    And type GetLastTradePriceOutput
                    ""${'"'}
                    <TradePrice>
                        <price>(number)</price>
                    </TradePrice>
                    ""${'"'}
                    When POST /stockquote
                    And request-header SOAPAction "http://example.com/GetLastTradePrice"
                    And request-body
                    ""${'"'}
                    <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header qontract_occurs="optional"/><soapenv:Body><qontract_GetLastTradePriceInput/></soapenv:Body></soapenv:Envelope>
                    ""${'"'}
                    Then status 200
                    And response-body
                    ""${'"'}
                    <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header qontract_occurs="optional"/><soapenv:Body><qontract_GetLastTradePriceOutput/></soapenv:Body></soapenv:Envelope>
                    ""${'"'}
        """.trimIndent()

        println(generatedGherkin)

        assertThat(Feature(generatedGherkin)).isEqualTo(Feature(expectedGherkin))
    }
}
