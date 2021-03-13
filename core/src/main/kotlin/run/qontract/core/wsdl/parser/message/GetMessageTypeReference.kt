package run.qontract.core.wsdl.parser.message

import run.qontract.core.pattern.XMLPattern
import run.qontract.core.value.XMLNode
import run.qontract.core.value.withoutNamespacePrefix
import run.qontract.core.wsdl.parser.SOAPMessageType
import run.qontract.core.wsdl.parser.WSDL
import run.qontract.core.wsdl.payload.EmptySOAPPayload
import run.qontract.core.wsdl.payload.SoapPayloadType

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