package run.qontract.core.wsdl.parser.operation

import run.qontract.core.pattern.ContractException
import run.qontract.core.pattern.Pattern
import run.qontract.core.pattern.XMLPattern

data class SOAPTypes(val types: Map<String, Pattern>) {
    fun statements(incrementalIndent: String): List<String> {
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