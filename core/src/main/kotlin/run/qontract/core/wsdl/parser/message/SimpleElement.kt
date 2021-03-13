package run.qontract.core.wsdl.parser.message

import run.qontract.core.pattern.ContractException
import run.qontract.core.pattern.XMLPattern
import run.qontract.core.value.StringValue
import run.qontract.core.value.XMLNode
import run.qontract.core.value.XMLValue
import run.qontract.core.value.withoutNamespacePrefix
import run.qontract.core.wsdl.parser.SOAPMessageType
import run.qontract.core.wsdl.parser.WSDL
import run.qontract.core.wsdl.parser.WSDLTypeInfo
import run.qontract.core.wsdl.payload.SOAPPayload
import run.qontract.core.wsdl.payload.SimpleTypedSOAPPayload

data class SimpleElement(val wsdlTypeReference: String, val element: XMLNode, val wsdl: WSDL) : SOAPElement {
    override fun getQontractTypes(qontractTypeName: String, existingTypes: Map<String, XMLPattern>, typeStack: Set<String>): WSDLTypeInfo {
        return createSimpleType(element, existingTypes).let {
            WSDLTypeInfo(it.first, it.second)
        }
    }

    override fun getSOAPPayload(
        soapMessageType: SOAPMessageType,
        nodeNameForSOAPBody: String,
        qontractTypeName: String,
        namespaces: Map<String, String>,
        typeInfo: WSDLTypeInfo
    ): SOAPPayload {
        return SimpleTypedSOAPPayload(soapMessageType, typeInfo.nodes.first() as XMLNode, namespaces)
    }

    private fun createSimpleType(element: XMLNode, types: Map<String, XMLPattern>): Pair<List<XMLValue>, Map<String, XMLPattern>> {
        val node = createSimpleType(element)
        return Pair(listOf(node), types)
    }
}

internal fun createSimpleType(element: XMLNode): XMLNode {
    val typeName = element.attributes.getValue("type").toStringValue().withoutNamespacePrefix()
    val value = when {
        primitiveStringTypes.contains(typeName) -> StringValue("(string)")
        primitiveNumberTypes.contains(typeName) -> StringValue("(number)")
        primitiveDateTypes.contains(typeName) -> StringValue("(datetime)")
        primitiveBooleanType.contains(typeName) -> StringValue("(boolean)")
        else -> throw ContractException("""Primitive type "$typeName" not recognized""")
    }

    val qontractAttributes = getQontractAttributes(element)

    return XMLNode(element.getAttributeValue("name").withoutNamespacePrefix(), qontractAttributes, listOf(value))
}
