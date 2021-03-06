package run.qontract.core.wsdl.parser

import run.qontract.core.pattern.ContractException
import run.qontract.core.pattern.Pattern
import run.qontract.core.pattern.XMLPattern
import run.qontract.core.wsdl.payload.SOAPPayload

data class SOAPOperationTypeInfo(
    val path: String,
    val operationName: String,
    val soapAction: String,
    val types: Map<String, Pattern>,
    val requestPayload: SOAPPayload,
    val responsePayload: SOAPPayload
) {
    fun toGherkinScenario(scenarioIndent: String = "", incrementalIndent: String = "  "): String {
        val titleStatement = listOf("Scenario: $operationName".prependIndent(scenarioIndent))

        val typeStatements = typesToGherkin(types, incrementalIndent)
        val requestStatements = requestStatements()
        val responseStatements = responseStatements()

        val statementIndent = "$scenarioIndent$incrementalIndent"
        val bodyStatements = typeStatements.plus(requestStatements).plus(responseStatements).map { it.prependIndent(statementIndent) }

        return titleStatement.plus(bodyStatements).joinToString("\n")
    }

    private fun requestStatements(): List<String> {
        val pathStatement = listOf("When POST $path")
        val soapActionHeaderStatement = when {
            soapAction.isNotBlank() -> listOf("""And request-header SOAPAction "$soapAction"""")
            else -> emptyList()
        }

        val requestBodyStatement = requestPayload.qontractStatement()
        return pathStatement.plus(soapActionHeaderStatement).plus(requestBodyStatement)
    }

    private fun responseStatements(): List<String> {
        val statusStatement = listOf("Then status 200")
        val responseBodyStatement = responsePayload.qontractStatement()
        return statusStatement.plus(responseBodyStatement)
    }

    private fun typesToGherkin(types: Map<String, Pattern>, incrementalIndent: String): List<String> {
        val typeStrings = types.entries.map { (typeName, type) ->
            if (type !is XMLPattern)
                throw ContractException("Unexpected type (name=$typeName) $type")

            val typeStringLines = type.toGherkinXMLNode().toPrettyStringValue().trim().lines()

            val indentedTypeString = when (typeStringLines.size) {
                0 -> ""
                1 -> typeStringLines.first().trim()
                else -> {
                    val firstLine = typeStringLines.first().trim()
                    val lastLine = typeStringLines.last().trim()

                    val rest = typeStringLines.drop(1).dropLast(1).map { it.prependIndent(incrementalIndent) }

                    listOf(firstLine).plus(rest).plus(lastLine).joinToString("\n")
                }
            }

            "And type $typeName\n\"\"\"\n$indentedTypeString\n\"\"\""
        }

        return when (typeStrings.size) {
            0 -> typeStrings
            else -> {
                val firstLine = typeStrings.first().removePrefix("And ")
                val adjustedFirstLine = "Given $firstLine"

                listOf(adjustedFirstLine).plus(typeStrings.drop(1))
            }
        }
    }
}

