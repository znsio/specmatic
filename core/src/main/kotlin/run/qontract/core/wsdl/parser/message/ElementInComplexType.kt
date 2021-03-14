package run.qontract.core.wsdl.parser.message

import run.qontract.core.pattern.XMLPattern
import run.qontract.core.value.XMLNode
import run.qontract.core.value.XMLValue
import run.qontract.core.wsdl.parser.WSDL
import run.qontract.core.wsdl.parser.WSDLTypeInfo

class ElementInComplexType(
    private val element: XMLNode,
    val wsdl: WSDL,
    private val parentTypeName: String
): ComplexTypeChild {
    override fun process(wsdlTypeInfo: WSDLTypeInfo, existingTypes: Map<String, XMLPattern>, typeStack: Set<String>): WSDLTypeInfo {
        val wsdlElement = wsdl.getWSDLElementType(parentTypeName, element)
        val (qontractTypeName, soapElement) = wsdlElement.getWSDLElement()

        val typeInfo = soapElement.getQontractTypes(qontractTypeName, existingTypes, typeStack)

        val newList: List<XMLValue> = wsdlTypeInfo.nodes.plus(typeInfo.nodes)
        val newTypes = wsdlTypeInfo.types.plus(typeInfo.types)
        return WSDLTypeInfo(newList, newTypes, typeInfo.namespacePrefixes)
    }
}