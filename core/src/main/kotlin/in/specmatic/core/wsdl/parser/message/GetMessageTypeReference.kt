package `in`.specmatic.core.wsdl.parser.message

import `in`.specmatic.core.pattern.XMLPattern
import `in`.specmatic.core.value.XMLNode
import `in`.specmatic.core.value.withoutNamespacePrefix
import `in`.specmatic.core.wsdl.parser.SOAPMessageType
import `in`.specmatic.core.wsdl.parser.WSDL
import `in`.specmatic.core.wsdl.payload.EmptySOAPPayload
import `in`.specmatic.core.wsdl.payload.SoapPayloadType

class GetMessageTypeReference(private val wsdl: WSDL, val messageTypeNode: XMLNode, private val soapMessageType: SOAPMessageType, private val existingTypes: Map<String, XMLPattern>, private val operationName: String) :
    MessageTypeInfoParser {

    override fun execute(): MessageTypeInfoParser {
        val messageName = messageTypeNode.getAttributeValue("message").withoutNamespacePrefix()
        val messageNode = wsdl.findMessageNode(messageName)
        val partNode = messageNode.firstNode()
            ?: return MessageTypeProcessingComplete(
                SoapPayloadType(
                    existingTypes,
                    EmptySOAPPayload(soapMessageType)
                )
            )

        val wsdlTypeReference = partNode.getAttributeValue("element", "element not found in \"part\" in message named $messageName")

        return ParseMessageStructureFromWSDLType(wsdl, wsdlTypeReference, soapMessageType, existingTypes, operationName)
    }
}