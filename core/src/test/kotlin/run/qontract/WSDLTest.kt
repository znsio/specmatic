package run.qontract

import org.junit.jupiter.api.Test
import run.qontract.core.parseWSDLIntoScenarios
import run.qontract.core.toGherkinFeature
import run.qontract.core.value.toXMLNode
import java.io.File

class WSDLTest {
    @Test
    fun temp() {
        val wsdlContent = File("/Users/joelrosario/tmp/soap/CustomerConfigurationInquiry.wsdl").readText()
        val wsdl = toXMLNode(wsdlContent)
        val listOfStubs = parseWSDLIntoScenarios(wsdl)
        val gherkin = toGherkinFeature("New SOAP contract", listOfStubs)
        println(gherkin)
    }
}
