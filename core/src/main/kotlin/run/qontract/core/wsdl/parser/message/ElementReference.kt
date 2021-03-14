package run.qontract.core.wsdl.parser.message

import run.qontract.core.value.XMLNode
import run.qontract.core.wsdl.parser.WSDL

data class ElementReference(val child: XMLNode, val wsdl: WSDL) : ChildElementType {
    override fun getWSDLElement(): Pair<String, WSDLElement> {
        val wsdlTypeReference = child.attributes.getValue("ref").toStringValue()
        val qontractTypeName = wsdlTypeReference.replace(':', '_')

        val resolvedChild = wsdl.getSOAPElement(wsdlTypeReference)
        return Pair(qontractTypeName, resolvedChild)
    }
}