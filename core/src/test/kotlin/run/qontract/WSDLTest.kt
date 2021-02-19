package run.qontract

import org.junit.jupiter.api.Test
import run.qontract.core.parseWSDLIntoScenarios
import run.qontract.core.toGherkinFeature
import run.qontract.core.value.toXMLNode
import java.io.File

class WSDLTest {
//    @Test
//    fun basicWSDL() {
//        val basicWSDL = """
//            <wsdl:definitions
//                 name="Untitled"
//                 targetNamespace="http://xmlns.example.com/1546843006089"
//                 xmlns:ns2="http://www.jio.ril.com/integration/services/common/schema/CustomerConfigurationInquiryConcrete/"
//                 xmlns:ns1="http://www.jio.ril.com/information/DataTypes/CustomerConfigurationInquiry/"
//                 xmlns:ns3="http://www.jio.ril.com/information/CanonicalDataModel/CustomerConfigurationInquiry/"
//                 xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/"
//                 xmlns:tns="http://xmlns.example.com/1546843006089"
//                 xmlns:ns0="http://www.jio.ril.com/integration/services/common/CustomerConfigurationInquiry/"
//                 xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
//                >
//                <wsdl:binding name="CustomerConfigurationInquiryBinding" type="tns:CustomerConfigurationInquiryV0dot9">
//                <wsdl:operation name="retrieveCustomerServiceConfiguration">
//                    <soap:operation style="document" soapAction="/retrieveCustomerServiceConfiguration"/>
//                    <wsdl:input>
//                        <soap:body use="literal"/>
//                    </wsdl:input>
//                    <wsdl:output>
//                        <soap:body use="literal"/>
//                    </wsdl:output>
//                    <wsdl:fault name="retrieveCustomerServiceConfigurationException">
//                        <soap:fault name="retrieveCustomerServiceConfigurationException" use="literal"/>
//                    </wsdl:fault>
//                </wsdl:operation>
//                </wsdl:binding>
//
//                    <wsdl:service name="CustomerConfigurationInquiry">
//        <wsdl:port name="CustomerConfigurationInquiry" binding="tns:CustomerConfigurationInquiryBinding">
//            <soap:address location="http://localhost:13041/CustomerConfigurationInquiryV0dot9"/>
//        </wsdl:port>
//    </wsdl:service>
//                <wsdl:types/>
//
//        """.trimIndent()
//        val wsdlContent = File("/Users/joelrosario/tmp/soap/CustomerConfigurationInquiry.wsdl").readText()
//        val wsdl = toXMLNode(wsdlContent)
//        val listOfStubs = parseWSDLIntoScenarios(wsdl)
//        val gherkin = toGherkinFeature("New SOAP contract", listOfStubs)
//        println(gherkin)
//    }
}
