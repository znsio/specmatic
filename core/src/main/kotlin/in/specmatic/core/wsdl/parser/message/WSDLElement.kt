package `in`.specmatic.core.wsdl.parser.message

import `in`.specmatic.core.pattern.XMLPattern
import `in`.specmatic.core.wsdl.parser.SOAPMessageType
import `in`.specmatic.core.wsdl.parser.WSDLTypeInfo
import `in`.specmatic.core.wsdl.payload.SOAPPayload

interface WSDLElement {
    fun deriveSpecmaticTypes(qontractTypeName: String, existingTypes: Map<String, XMLPattern>, typeStack: Set<String>): WSDLTypeInfo

    fun getSOAPPayload(
        soapMessageType: SOAPMessageType,
        nodeNameForSOAPBody: String,
        qontractTypeName: String,
        namespaces: Map<String, String>,
        typeInfo: WSDLTypeInfo
    ): SOAPPayload
}