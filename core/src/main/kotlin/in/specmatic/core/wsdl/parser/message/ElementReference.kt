package `in`.specmatic.core.wsdl.parser.message

import `in`.specmatic.core.value.XMLNode
import `in`.specmatic.core.wsdl.parser.WSDL

data class ElementReference(val child: XMLNode, val wsdl: WSDL) : ChildElementType {
    override fun getWSDLElement(): Pair<String, WSDLElement> {
        val wsdlTypeReference = child.attributes.getValue("ref").toStringValue()
        val qontractTypeName = wsdlTypeReference.replace(':', '_')

        val resolvedChild = wsdl.getSOAPElement(wsdlTypeReference)
        return Pair(qontractTypeName, resolvedChild)
    }
}