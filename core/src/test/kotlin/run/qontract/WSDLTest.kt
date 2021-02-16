package run.qontract

import org.junit.jupiter.api.Test
import run.qontract.core.HttpRequest
import run.qontract.core.HttpResponse
import run.qontract.core.pattern.ContractException
import run.qontract.core.utilities.parseXML
import run.qontract.core.value.XMLNode
import run.qontract.mock.ScenarioStub
import java.io.File

fun parseWSDLIntoScenarios(wsdl: XMLNode): List<ScenarioStub> {
    val portNodeName = "port"
    val url = lookupAttribute(wsdl, "service.$portNodeName.address", "location")
    val portType = lookupXMLNode(wsdl, portNodeName)

    val types = lookupXMLNode(wsdl, "types")
    val messages = lookupXMLNode(wsdl, "message")

    return portType.findChildrenByName("operation").map {
        val method = "POST"

        val requestBody = inputToRequest(it)

        val request =
            HttpRequest(method = method, path = url, headers = mapOf("Content-Type" to "application/xml"), body = requestBody)
        val response = HttpResponse.OK

        ScenarioStub(request, response)
    }
}

private fun lookupAttribute(wsdl: XMLNode, path: String, attributeName: String): String {
    val node = lookupXMLNode(wsdl, path)
    return node.attributes[attributeName]?.toString() ?: throw ContractException("Couldn't find attribute $attributeName at path $path")
}

private fun lookupXMLNode(wsdl: XMLNode, nodeName: String): XMLNode = wsdl.findFirstChildByPath(nodeName) ?: throw ContractException("Couldn't find $nodeName")

fun inputToRequest(xmlNode: XMLNode): XMLNode {
    val soapAction = xmlNode.attributes["soapAction"]

    return XMLNode(parseXML("<xml/>"))
}

class WSDLTest {
    @Test
    fun temp() {
//        val scenario = parseWSDLIntoScenarios(XMLNode(File("/Users/joelrosario/tmp/soap/stockquote.wsdl").readText()))
    }
}