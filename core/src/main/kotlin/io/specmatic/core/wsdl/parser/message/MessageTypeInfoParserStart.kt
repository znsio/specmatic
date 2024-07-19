package io.specmatic.core.wsdl.parser.message

import io.specmatic.core.pattern.XMLPattern
import io.specmatic.core.value.XMLNode
import io.specmatic.core.wsdl.parser.SOAPMessageType
import io.specmatic.core.wsdl.parser.WSDL
import io.specmatic.core.wsdl.payload.EmptyHTTPBodyPayload
import io.specmatic.core.wsdl.payload.SoapPayloadType

class MessageTypeInfoParserStart(private val wsdl: WSDL, private val portOperationNode: XMLNode, private val soapMessageType: SOAPMessageType, private val existingTypes: Map<String, XMLPattern>, private val operationName: String):
    MessageTypeInfoParser {
    override fun execute(): MessageTypeInfoParser {
        val messageTypeNode = getMessageDescriptionFromPortType()
            ?: return MessageTypeProcessingComplete(SoapPayloadType(existingTypes, EmptyHTTPBodyPayload()))

        return GetMessageTypeReference(wsdl, messageTypeNode, soapMessageType, existingTypes, operationName)
    }

    private fun getMessageDescriptionFromPortType() = portOperationNode.findFirstChildByName(soapMessageType.messageTypeName)
}