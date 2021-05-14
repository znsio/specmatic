package `in`.specmatic.core.wsdl.parser.message

import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.pattern.XMLPattern
import `in`.specmatic.core.utilities.capitalizeFirstChar
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.XMLNode
import `in`.specmatic.core.value.toXMLNode
import `in`.specmatic.core.value.xmlNode
import `in`.specmatic.core.wsdl.parser.SOAPMessageType
import `in`.specmatic.core.wsdl.parser.WSDL
import `in`.specmatic.core.wsdl.payload.SoapPayloadType

class ParseMessageWithoutElementRef(
    val messageName: String,
    val messageNamespacePrefix: String,
    val partName: String,
    val wsdlTypeReference: String,
    val soapMessageType: SOAPMessageType,
    val existingTypes: Map<String, XMLPattern>,
    val operationName: String,
    val wsdl: WSDL,
) : MessageTypeInfoParser {
    override fun execute(): MessageTypeInfoParser {
        val wsdlNamespaces = wsdl.getNamespaces()

        val (qualification, qualifiedMessageName) = when {
            messageNamespacePrefix.isNotBlank() -> {
                val qualification = QualificationWithoutSchema(listOf(messageNamespacePrefix), "$messageNamespacePrefix:$messageName")
                Pair(qualification, qualification.nodeName)
            }
            else -> Pair(null, messageName)
        }

        val topLevelNode = xmlNode("element", mapOf("name" to qualifiedMessageName)) {
            parentNamespaces(wsdlNamespaces)

            xmlNode("complexType") {
                xmlNode("sequence") {
                    xmlNode("element", mapOf("name" to partName, "type" to wsdlTypeReference))
                }
            }
        }

        val topLevelElement = ComplexElement(wsdlTypeReference, topLevelNode, wsdl, qualification)

        val qontractTypeName = "${operationName.replace(":", "_")}${soapMessageType.messageTypeName.capitalizeFirstChar()} "

        val typeInfo = topLevelElement.getQontractTypes(qontractTypeName, existingTypes, emptySet())

        val namespaces: Map<String, String> = wsdl.getNamespaces(typeInfo)
        val nodeNameForSOAPBody = (typeInfo.nodes.first() as XMLNode).realName

        val soapPayload = topLevelElement.getSOAPPayload(soapMessageType, nodeNameForSOAPBody, qontractTypeName, namespaces, typeInfo)

        return MessageTypeProcessingComplete(SoapPayloadType(typeInfo.types, soapPayload))
    }

}