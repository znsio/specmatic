package run.qontract.core.wsdl.parser.message

import run.qontract.core.pattern.XMLPattern
import run.qontract.core.value.StringValue
import run.qontract.core.value.XMLNode
import run.qontract.core.wsdl.parser.SOAPMessageType
import run.qontract.core.wsdl.parser.WSDL
import run.qontract.core.wsdl.payload.SoapPayloadType

data class ParseMessageStructureFromWSDLType(private val wsdl: WSDL, private val wsdlTypeReference: String, private val soapMessageType: SOAPMessageType, private val existingTypes: Map<String, XMLPattern>, private val operationName: String) : MessageTypeInfoParser {
    override fun execute(): MessageTypeInfoParser {
        val topLevelElement = wsdl.getSOAPElement(wsdlTypeReference)

        val qontractTypeName = "${operationName.replace(":", "_")}${soapMessageType.messageTypeName.capitalize()}"

        val typeInfo = topLevelElement.getQontractTypes(qontractTypeName, existingTypes, emptySet())

        val namespaces: Map<String, String> = wsdl.getNamespaces(typeInfo)
        val nodeNameForSOAPBody = (typeInfo.nodes.first() as XMLNode).realName

        val soapPayload = topLevelElement.getSOAPPayload(soapMessageType, nodeNameForSOAPBody, qontractTypeName, namespaces, typeInfo)

        return MessageTypeProcessingComplete(SoapPayloadType(typeInfo.types, soapPayload))
    }
}

fun getQontractAttributes(element: XMLNode): Map<String, StringValue> {
    return when {
        elementIsOptional(element) -> mapOf(OCCURS_ATTRIBUTE_NAME to StringValue(OPTIONAL_ATTRIBUTE_VALUE))
        multipleElementsCanExist(element) -> mapOf(OCCURS_ATTRIBUTE_NAME to StringValue(MULTIPLE_ATTRIBUTE_VALUE))
        else -> emptyMap()
    }
}

private fun multipleElementsCanExist(element: XMLNode): Boolean {
    return element.attributes.containsKey("maxOccurs")
            && (element.attributes["maxOccurs"]?.toStringValue() == "unbounded"
            || element.attributes.getValue("maxOccurs").toStringValue().toInt() > 1)
}

private fun elementIsOptional(element: XMLNode): Boolean {
    return element.attributes["minOccurs"]?.toStringValue() == "0" && !element.attributes.containsKey("maxOccurs")
}

fun isPrimitiveType(node: XMLNode): Boolean {
    val type = node.attributes.getValue("type").toStringValue()
    val namespace = node.resolveNamespace(type)

    if(namespace.isBlank())
        return primitiveTypes.contains(type)

    return namespace == primitiveNamespace
}

val primitiveStringTypes = listOf(
    "string",
    "anyType",
    "duration",
    "time",
    "date",
    "gYearMonth",
    "gYear",
    "gMonthDay",
    "gDay",
    "gMonth",
    "hexBinary",
    "base64Binary",
    "anyURI", // TODO maybe this can be converted to URL type
    "QName",
    "NOTATION"
)
val primitiveNumberTypes = listOf("int", "integer", "long", "decimal", "float", "double", "numeric")
val primitiveDateTypes = listOf("dateTime")
val primitiveBooleanType = listOf("boolean")
val primitiveTypes = primitiveStringTypes.plus(primitiveNumberTypes).plus(primitiveDateTypes).plus(primitiveBooleanType)

internal const val primitiveNamespace = "http://www.w3.org/2001/XMLSchema"

const val OCCURS_ATTRIBUTE_NAME = "qontract_occurs"
const val OPTIONAL_ATTRIBUTE_VALUE = "optional"
const val MULTIPLE_ATTRIBUTE_VALUE = "multiple"
