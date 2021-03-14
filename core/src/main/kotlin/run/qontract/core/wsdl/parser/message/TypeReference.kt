package run.qontract.core.wsdl.parser.message

import run.qontract.core.value.XMLNode
import run.qontract.core.wsdl.parser.WSDL
import run.qontract.core.wsdl.parser.hasSimpleTypeAttribute

data class TypeReference(val child: XMLNode, val wsdl: WSDL): ChildElementType {
    override fun getWSDLElement(): Pair<String, WSDLElement> {
        val wsdlTypeReference = child.attributes.getValue("type").toStringValue()
        val qontractTypeName = wsdlTypeReference.replace(':', '_')

        val element = when {
            hasSimpleTypeAttribute(child) -> SimpleElement(wsdlTypeReference, child, wsdl)
            else -> ComplexElement(wsdlTypeReference, child, wsdl)
        }

        return Pair(qontractTypeName, element)
    }
}