package run.qontract.core.wsdl

import run.qontract.core.pattern.Pattern

data class SoapPayloadType(val types: Map<String, Pattern>, val soapPayload: SOAPPayload)