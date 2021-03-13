package run.qontract.core.wsdl.parser.message

import run.qontract.core.pattern.XMLPattern
import run.qontract.core.wsdl.parser.SOAPMessageType
import run.qontract.core.wsdl.parser.WSDLTypeInfo
import run.qontract.core.wsdl.payload.SOAPPayload

interface WSDLPayloadElement {
    fun getQontractTypes(qontractTypeName: String, existingTypes: Map<String, XMLPattern>, typeStack: Set<String>): WSDLTypeInfo

    fun getSOAPPayload(
        soapMessageType: SOAPMessageType,
        nodeNameForSOAPBody: String,
        qontractTypeName: String,
        namespaces: Map<String, String>,
        typeInfo: WSDLTypeInfo
    ): SOAPPayload
}