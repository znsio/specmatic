package io.specmatic.core.wsdl.parser.message

import io.specmatic.core.value.XMLNode
import io.specmatic.core.wsdl.parser.WSDL
import io.specmatic.core.wsdl.parser.hasSimpleTypeAttribute

data class TypeReference(val child: XMLNode, val wsdl: WSDL): ChildElementType {
    override fun getWSDLElement(): Pair<String, WSDLElement> {
        val wsdlTypeReference = child.attributes.getValue("type").toStringLiteral()
        val specmaticTypeName = wsdlTypeReference.replace(':', '_')

        val element = when {
            hasSimpleTypeAttribute(child) -> SimpleElement(wsdlTypeReference, child, wsdl)
            else -> ReferredType(wsdlTypeReference, child, wsdl)
        }

        return Pair(specmaticTypeName, element)
    }
}