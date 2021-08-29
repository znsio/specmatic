package `in`.specmatic.core.wsdl.parser.message

import `in`.specmatic.core.pattern.XMLPattern
import `in`.specmatic.core.utilities.capitalizeFirstChar
import `in`.specmatic.core.value.FullyQualifiedName
import `in`.specmatic.core.value.XMLNode
import `in`.specmatic.core.value.xmlNode
import `in`.specmatic.core.wsdl.parser.SOAPMessageType
import `in`.specmatic.core.wsdl.parser.WSDL
import `in`.specmatic.core.wsdl.payload.SoapPayloadType

class ParseMessageWithoutElementRef(
    val fullyQualifiedMessageName: FullyQualifiedName,
    val partName: String,
    val fullyQualifiedTypeName: FullyQualifiedName,
    val soapMessageType: SOAPMessageType,
    val existingTypes: Map<String, XMLPattern>,
    val operationName: String,
    val wsdl: WSDL,
) : MessageTypeInfoParser {
    override fun execute(): MessageTypeInfoParser {
        val (qualification, qualifiedMessageName) = when {
            fullyQualifiedMessageName.prefix.isNotBlank() -> {
                val qualification = QualificationWithoutSchema(listOf(wsdl.getSchemaNamespacePrefix(fullyQualifiedMessageName.namespace)), fullyQualifiedMessageName.qname)
                Pair(qualification, qualification.nodeName)
            }
            else -> Pair(null, fullyQualifiedMessageName.localName)
        }

        val topLevelNode = xmlNode("element", mapOf("name" to qualifiedMessageName)) {
            parentNamespaces(wsdl.allNamespaces())

            xmlNode("complexType") {
                xmlNode("sequence") {
                    xmlNode("element", mapOf("name" to partName, "type" to fullyQualifiedTypeName.qname))
                }
            }
        }

        val topLevelElement = ComplexElement(fullyQualifiedTypeName.qname, topLevelNode, wsdl, qualification)

        val qontractTypeName = "${operationName.replace(":", "_")}${soapMessageType.messageTypeName.capitalizeFirstChar()} "

        val typeInfo = topLevelElement.getGherkinTypes(qontractTypeName, existingTypes, emptySet())

        val namespaces: Map<String, String> = wsdl.getNamespaces(typeInfo)
        val nodeNameForSOAPBody = (typeInfo.nodes.first() as XMLNode).realName

        val soapPayload = topLevelElement.getSOAPPayload(soapMessageType, nodeNameForSOAPBody, qontractTypeName, namespaces, typeInfo)

        return MessageTypeProcessingComplete(SoapPayloadType(typeInfo.types, soapPayload))
    }
}
