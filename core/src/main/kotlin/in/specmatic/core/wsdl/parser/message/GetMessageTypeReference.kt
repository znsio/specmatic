package `in`.specmatic.core.wsdl.parser.message

import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.pattern.XMLPattern
import `in`.specmatic.core.value.XMLNode
import `in`.specmatic.core.value.namespacePrefix
import `in`.specmatic.core.value.withoutNamespacePrefix
import `in`.specmatic.core.wsdl.parser.SOAPMessageType
import `in`.specmatic.core.wsdl.parser.WSDL
import `in`.specmatic.core.wsdl.payload.EmptySOAPPayload
import `in`.specmatic.core.wsdl.payload.SoapPayloadType

class GetMessageTypeReference(private val wsdl: WSDL, val messageTypeNode: XMLNode, private val soapMessageType: SOAPMessageType, private val existingTypes: Map<String, XMLPattern>, private val operationName: String) :
    MessageTypeInfoParser {

    override fun execute(): MessageTypeInfoParser {
        val messageNameWithPrefix = messageTypeNode.getAttributeValue("message")
        val messageName = messageNameWithPrefix.withoutNamespacePrefix()
        val messageNode = wsdl.findMessageNode(messageName)
        val partNode = messageNode.firstNode()
            ?: return MessageTypeProcessingComplete(
                SoapPayloadType(
                    existingTypes,
                    EmptySOAPPayload(soapMessageType)
                )
            )

        return when {
            partNode.attributes.containsKey("element") -> {
                val wsdlTypeReference = partNode.attributes.getValue("element").toStringValue()
                ParseMessageWithElementRef(wsdl, wsdlTypeReference, soapMessageType, existingTypes, operationName)
            }
            partNode.attributes.containsKey("type") -> {
                val messageNamespacePrefix = messageNameWithPrefix.namespacePrefix()
                val wsdlTypeReference = partNode.attributes.getValue("type").toStringValue()
                val partName = partNode.getAttributeValue("name", "Part node of message named $messageName does not have a name.")
                ParseMessageWithoutElementRef(messageName, messageNamespacePrefix, partName, wsdlTypeReference, soapMessageType, existingTypes, operationName, wsdl)
            }
            else -> throw ContractException("Part node of message named $messageName should contain either an element attribute or a type attribute.")
        }
    }
}