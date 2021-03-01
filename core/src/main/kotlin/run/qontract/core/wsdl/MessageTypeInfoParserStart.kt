package run.qontract.core.wsdl

import run.qontract.core.pattern.Pattern
import run.qontract.core.value.XMLNode

class MessageTypeInfoParserStart(private val wsdl: WSDL, private val portOperationNode: XMLNode, private val soapMessageType: SOAPMessageType, private val existingTypes: Map<String, Pattern>, private val operationName: String):
    MessageTypeInfoParser {
    override fun execute(): MessageTypeInfoParser {
        val messageTypeNode = getMessageDescriptionFromPortType()
            ?: return MessageTypeProcessingComplete(SoapPayloadType(existingTypes, EmptyHTTPBodyPayload()))

        return GetMessageTypeReference(wsdl, messageTypeNode, soapMessageType, existingTypes, operationName)
    }

    private fun getMessageDescriptionFromPortType() = portOperationNode.findFirstChildByName(soapMessageType.messageTypeName)
}