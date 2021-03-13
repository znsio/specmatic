package run.qontract.core.wsdl.payload

import run.qontract.core.pattern.XMLPattern

data class SoapPayloadType(val types: Map<String, XMLPattern>, val soapPayload: SOAPPayload)