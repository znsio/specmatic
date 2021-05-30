package `in`.specmatic.core.wsdl.parser.message

import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.pattern.XMLPattern
import `in`.specmatic.core.value.XMLNode
import `in`.specmatic.core.wsdl.parser.SOAPMessageType
import `in`.specmatic.core.wsdl.parser.WSDL
import `in`.specmatic.core.wsdl.payload.EmptySOAPPayload
import `in`.specmatic.core.wsdl.payload.SoapPayloadType

class GetMessageTypeReference(private val wsdl: WSDL, val messageTypeNode: XMLNode, private val soapMessageType: SOAPMessageType, private val existingTypes: Map<String, XMLPattern>, private val operationName: String) :
    MessageTypeInfoParser {

    override fun execute(): MessageTypeInfoParser {
        val fullyQualifiedMessageName = messageTypeNode.fullyQualifiedNameFromAttribute("message")
        val messageNode = wsdl.findMessageNode(fullyQualifiedMessageName)

        val partNode = messageNode.firstNode()
            ?: return MessageTypeProcessingComplete(
                SoapPayloadType(
                    existingTypes,
                    EmptySOAPPayload(soapMessageType)
                )
            )

        return when {
            partNode.attributes.containsKey("element") -> {
                val fullyQualifiedTypeName = partNode.fullyQualifiedNameFromAttribute("element")
                ParseMessageWithElementRef(wsdl, fullyQualifiedTypeName, soapMessageType, existingTypes, operationName)
            }
            partNode.attributes.containsKey("type") -> {
                val fullyQualifiedTypeName = partNode.fullyQualifiedNameFromAttribute("type")
                val partName = partNode.getAttributeValue("name", "Part node of message named ${fullyQualifiedMessageName.localName} does not have a name.")
                ParseMessageWithoutElementRef(fullyQualifiedMessageName, partName, fullyQualifiedTypeName, soapMessageType, existingTypes, operationName, wsdl)
            }
            else -> throw ContractException("Part node of message named ${fullyQualifiedMessageName.localName} should contain either an element attribute or a type attribute.")
        }
    }
}
