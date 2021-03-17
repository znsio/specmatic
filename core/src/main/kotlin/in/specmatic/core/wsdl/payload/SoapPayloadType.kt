package `in`.specmatic.core.wsdl.payload

import `in`.specmatic.core.pattern.XMLPattern

data class SoapPayloadType(val types: Map<String, XMLPattern>, val soapPayload: SOAPPayload)