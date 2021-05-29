package `in`.specmatic.core.wsdl.parser.message

import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.pattern.XMLPattern
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.XMLNode
import `in`.specmatic.core.value.XMLValue
import `in`.specmatic.core.value.localName
import `in`.specmatic.core.wsdl.parser.SOAPMessageType
import `in`.specmatic.core.wsdl.parser.WSDL
import `in`.specmatic.core.wsdl.parser.WSDLTypeInfo
import `in`.specmatic.core.wsdl.payload.SOAPPayload
import `in`.specmatic.core.wsdl.payload.SimpleTypedSOAPPayload

data class SimpleElement(val wsdlTypeReference: String, val element: XMLNode, val wsdl: WSDL) : WSDLElement {
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
    val typeName = element.attributes.getValue("type").toStringValue().localName()
    val value = when(typeName) {
        in primitiveStringTypes -> StringValue("(string)")
        in primitiveNumberTypes -> StringValue("(number)")
        in primitiveDateTypes -> StringValue("(datetime)")
        in primitiveBooleanType -> StringValue("(boolean)")
        "anyType" -> StringValue("(anything)")

        else -> throw ContractException("""Primitive type "$typeName" not recognized""")
    }

    val qontractAttributes = getQontractAttributes(element)

    return XMLNode(element.getAttributeValue("name").localName(), qontractAttributes, listOf(value))
}
