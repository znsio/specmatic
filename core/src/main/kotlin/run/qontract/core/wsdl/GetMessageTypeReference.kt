package run.qontract.core.wsdl

import run.qontract.core.pattern.ContractException
import run.qontract.core.pattern.Pattern
import run.qontract.core.value.XMLNode
import run.qontract.core.value.withoutNamespacePrefix

class GetMessageTypeReference(private val wsdl: WSDL, private val messageTypeNode: XMLNode, private val soapMessageType: SOAPMessageType, private val existingTypes: Map<String, Pattern>, private val operationName: String) :
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

        val wsdlTypeReference = partNode.attributes["element"]?.toStringValue() ?: throw ContractException(
            "element not found in \"part\" in message named $messageName"
        )

        return ParseMessageStructure(wsdl, wsdlTypeReference, soapMessageType, existingTypes, operationName)
    }
}