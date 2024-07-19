package io.specmatic.core.wsdl.payload

import io.specmatic.core.pattern.XMLPattern

data class SoapPayloadType(val types: Map<String, XMLPattern>, val soapPayload: SOAPPayload)