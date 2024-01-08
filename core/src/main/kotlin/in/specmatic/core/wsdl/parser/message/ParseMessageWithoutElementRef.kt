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
    private val fullyQualifiedMessageName: FullyQualifiedName,
    private val partName: String,
    private val fullyQualifiedTypeName: FullyQualifiedName,
    val soapMessageType: SOAPMessageType,
    val existingTypes: Map<String, XMLPattern>,
    private val operationName: String,
    val wsdl: WSDL,
) : MessageTypeInfoParser {
    override fun execute(): MessageTypeInfoParser {
        val (qualification, qualifiedMessageName) = when {
            fullyQualifiedMessageName.prefix.isNotBlank() -> {
                val qualification = QualificationWithoutSchema(listOf(wsdl.getSchemaNamespacePrefix(fullyQualifiedMessageName.namespace)), fullyQualifiedMessageName.qName)
                Pair(qualification, qualification.nodeName)
            }
            else -> Pair(null, fullyQualifiedMessageName.localName)
        }

        val topLevelNode = xmlNode("element", mapOf("name" to qualifiedMessageName)) {
            parentNamespaces(wsdl.allNamespaces())

            xmlNode("complexType") {
                xmlNode("sequence") {
                    xmlNode("element", mapOf("name" to partName, "type" to fullyQualifiedTypeName.qName))
                }
            }
        }

        val topLevelElement = ComplexElement(fullyQualifiedTypeName.qName, topLevelNode, wsdl, qualification)

        val specmaticTypeName = "${operationName.replace(":", "_")}${soapMessageType.messageTypeName.capitalizeFirstChar()} "

        val typeInfo = topLevelElement.deriveSpecmaticTypes(specmaticTypeName, existingTypes, emptySet())

        val namespaces: Map<String, String> = wsdl.getNamespaces(typeInfo)
        val nodeNameForSOAPBody = (typeInfo.nodes.first() as XMLNode).realName

        val soapPayload = topLevelElement.getSOAPPayload(soapMessageType, nodeNameForSOAPBody, specmaticTypeName, namespaces, typeInfo)

        return MessageTypeProcessingComplete(SoapPayloadType(typeInfo.types, soapPayload))
    }
}
