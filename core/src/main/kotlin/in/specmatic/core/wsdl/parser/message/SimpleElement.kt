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
    override fun deriveSpecmaticTypes(qontractTypeName: String, existingTypes: Map<String, XMLPattern>, typeStack: Set<String>): WSDLTypeInfo {
        return createSimpleType(element, wsdl).let { (nodes, prefix) ->
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

}

fun createSimpleType(element: XMLNode, wsdl: WSDL, actualElement: XMLNode? = null): Pair<List<XMLValue>, String?> {
    val value = elementTypeValue(element)

    val resolvedElement = actualElement ?: element

    val qontractAttributes = deriveSpecmaticAttributes(resolvedElement)
    val fqname = resolvedElement.fullyQualifiedName(wsdl)
    val prefix = fqname.prefix.ifBlank { null }

    return Pair(listOf(XMLNode(fqname.qname, qontractAttributes, listOf(value))), prefix)
}

fun elementTypeValue(element: XMLNode): StringValue = when (val typeName = simpleTypeName(element)) {
    in primitiveStringTypes -> StringValue("(string)")
    in primitiveNumberTypes -> StringValue("(number)")
    in primitiveDateTypes -> StringValue("(datetime)")
    in primitiveBooleanType -> StringValue("(boolean)")
    "anyType" -> StringValue("(anything)")

    else -> throw ContractException("""Primitive type "$typeName" not recognized""")
}

fun simpleTypeName(element: XMLNode): String {
    return fromTypeAttribute(element) ?: fromRestriction(element) ?: throw ContractException("Could not find type for node ${element.displayableValue()}")
}

fun fromRestriction(element: XMLNode): String? {
    return element.childNodes.find { it is XMLNode && it.name == "restriction" }?.let {
        it as XMLNode
        it.getAttributeValue("base").localName()
    }
}

fun fromTypeAttribute(element: XMLNode): String? {
    return element.attributes["type"]?.let {
        it.toStringLiteral().localName()
    }
}

fun fromNameAttribute(element: XMLNode): String? {
    return element.attributes["name"]?.let {
        it.toStringLiteral().localName()
    }
}
