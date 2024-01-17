package `in`.specmatic.core.wsdl.parser.message

import `in`.specmatic.core.value.XMLNode
import `in`.specmatic.core.wsdl.parser.WSDL

data class ElementReference(val child: XMLNode, val wsdl: WSDL) : ChildElementType {
    override fun getWSDLElement(): Pair<String, WSDLElement> {
        val wsdlTypeReference = child.attributes.getValue("ref").toStringLiteral()
        val fullyQualifiedName = child.fullyQualifiedNameFromAttribute("ref")
        val specmaticTypeName = wsdlTypeReference.replace(':', '_')

        val otherRefAttributes = child.attributes.minus("ref")

        val resolvedChild = wsdl.getSOAPElement(fullyQualifiedName, child.schema, otherRefAttributes)
        return Pair(specmaticTypeName, resolvedChild)
    }
}