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
    override fun getGherkinTypes(qontractTypeName: String, existingTypes: Map<String, XMLPattern>, typeStack: Set<String>): WSDLTypeInfo {
        return createSimpleType(element).let { (nodes, prefix) ->
            if(prefix != null) {
                WSDLTypeInfo(nodes = nodes, existingTypes, setOf(prefix))
            } else {
                WSDLTypeInfo(nodes, existingTypes)
            }
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

    private fun createSimpleType(element: XMLNode): Pair<List<XMLValue>, String?> {
        val value = when (val typeName = element.attributes.getValue("type").toStringLiteral().localName()) {
            in primitiveStringTypes -> StringValue("(string)")
            in primitiveNumberTypes -> StringValue("(number)")
            in primitiveDateTypes -> StringValue("(datetime)")
            in primitiveBooleanType -> StringValue("(boolean)")
            "anyType" -> StringValue("(anything)")

            else -> throw ContractException("""Primitive type "$typeName" not recognized""")
        }

        val qontractAttributes = getQontractAttributes(element)
        val fqname = element.fullyQualifiedName(wsdl)
        val prefix = if (fqname.prefix.isNotBlank()) {
            fqname.prefix
        } else
            null

        return Pair(listOf(XMLNode(fqname.qname, qontractAttributes, listOf(value))), prefix)
    }
}

