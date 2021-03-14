package run.qontract.core.wsdl.parser.message

import run.qontract.core.pattern.ContractException
import run.qontract.core.value.XMLNode
import run.qontract.core.wsdl.parser.WSDL
import run.qontract.core.wsdl.parser.hasSimpleTypeAttribute

data class InlineType(val parentTypeName: String, val child: XMLNode, val wsdl: WSDL): ChildElementType {
    override fun getWSDLElement(): Pair<String, WSDLElement> {
        val wsdlTypeReference = ""

        val elementName = child.attributes["name"]
            ?: throw ContractException("Element does not have a name: $child")
        val qontractTypeName = "${parentTypeName}_$elementName"

        val element = when {
            hasSimpleTypeAttribute(child) -> SimpleElement(wsdlTypeReference, child, wsdl)
            else -> ComplexElement(wsdlTypeReference, child, wsdl)
        }

        return Pair(qontractTypeName, element)
    }
}