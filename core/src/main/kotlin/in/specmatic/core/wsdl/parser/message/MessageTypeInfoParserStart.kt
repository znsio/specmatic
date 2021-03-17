package `in`.specmatic.core.wsdl.parser.message

import `in`.specmatic.core.pattern.XMLPattern
import `in`.specmatic.core.value.XMLNode
import `in`.specmatic.core.wsdl.parser.SOAPMessageType
import `in`.specmatic.core.wsdl.parser.WSDL
import `in`.specmatic.core.wsdl.payload.EmptyHTTPBodyPayload
import `in`.specmatic.core.wsdl.payload.SoapPayloadType

class MessageTypeInfoParserStart(private val wsdl: WSDL, private val portOperationNode: XMLNode, private val soapMessageType: SOAPMessageType, private val existingTypes: Map<String, XMLPattern>, private val operationName: String):
    MessageTypeInfoParser {
    override fun execute(): MessageTypeInfoParser {
        val messageTypeNode = getMessageDescriptionFromPortType()
            ?: return MessageTypeProcessingComplete(SoapPayloadType(existingTypes, EmptyHTTPBodyPayload()))

        return GetMessageTypeReference(wsdl, messageTypeNode, soapMessageType, existingTypes, operationName)
    }

    private fun getMessageDescriptionFromPortType() = portOperationNode.findFirstChildByName(soapMessageType.messageTypeName)
}