package run.qontract.core.wsdl.payload

import run.qontract.core.pattern.Pattern
import run.qontract.core.wsdl.payload.SOAPPayload

data class SoapPayloadType(val types: Map<String, Pattern>, val soapPayload: SOAPPayload)