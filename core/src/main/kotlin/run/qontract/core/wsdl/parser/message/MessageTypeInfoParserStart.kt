package run.qontract.core.wsdl.parser.message

import run.qontract.core.pattern.XMLPattern
import run.qontract.core.value.XMLNode
import run.qontract.core.wsdl.parser.SOAPMessageType
import run.qontract.core.wsdl.parser.WSDL
import run.qontract.core.wsdl.payload.EmptyHTTPBodyPayload
import run.qontract.core.wsdl.payload.SoapPayloadType

class MessageTypeInfoParserStart(private val wsdl: WSDL, private val portOperationNode: XMLNode, private val soapMessageType: SOAPMessageType, private val existingTypes: Map<String, XMLPattern>, private val operationName: String):
    MessageTypeInfoParser {
    override fun execute(): MessageTypeInfoParser {
        val messageTypeNode = getMessageDescriptionFromPortType()
            ?: return MessageTypeProcessingComplete(SoapPayloadType(existingTypes, EmptyHTTPBodyPayload()))

        return GetMessageTypeReference(wsdl, messageTypeNode, soapMessageType, existingTypes, operationName)
    }

    private fun getMessageDescriptionFromPortType() = portOperationNode.findFirstChildByName(soapMessageType.messageTypeName)
}