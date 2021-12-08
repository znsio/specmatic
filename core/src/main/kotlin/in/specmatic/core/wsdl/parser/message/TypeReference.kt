package `in`.specmatic.core.wsdl.parser.message

import `in`.specmatic.core.value.XMLNode
import `in`.specmatic.core.wsdl.parser.WSDL
import `in`.specmatic.core.wsdl.parser.hasSimpleTypeAttribute

data class TypeReference(val child: XMLNode, val wsdl: WSDL): ChildElementType {
    override fun getWSDLElement(): Pair<String, WSDLElement> {
        val wsdlTypeReference = child.attributes.getValue("type").toStringLiteral()
        val qontractTypeName = wsdlTypeReference.replace(':', '_')

        val element = when {
            hasSimpleTypeAttribute(child) -> SimpleElement(wsdlTypeReference, child, wsdl)
            else -> ReferredType(wsdlTypeReference, child, wsdl)
        }

        return Pair(qontractTypeName, element)
    }
}