package run.qontract.core.wsdl.parser.operation

import run.qontract.core.pattern.XMLPattern
import run.qontract.core.wsdl.payload.SOAPPayload

data class SOAPOperationTypeInfo(val operationName: String, val request: SOAPRequest, val response: SOAPResponse, val types: SOAPTypes) {
    constructor(
        path: String,
        operationName: String,
        soapAction: String,
        types: Map<String, XMLPattern>,
        requestPayload: SOAPPayload,
        responsePayload: SOAPPayload
    ) : this(operationName, SOAPRequest(path, operationName, soapAction, requestPayload), SOAPResponse(responsePayload), SOAPTypes(types))

    fun toGherkinScenario(scenarioIndent: String = "", incrementalIndent: String = "  "): String {
        val titleStatement = listOf("Scenario: $operationName".prependIndent(scenarioIndent))

        val statementIndent = "$scenarioIndent$incrementalIndent"
        val bodyStatements =
            types.statements(incrementalIndent)
            .plus(request.statements())
            .plus(response.statements())
            .map { it.prependIndent(statementIndent) }

        return titleStatement.plus(bodyStatements).joinToString("\n")
    }
}
