package run.qontract.core.wsdl.parser.operation

import run.qontract.core.pattern.XMLPattern

data class SOAPTypes(val types: Map<String, XMLPattern>) {
    fun statements(): List<String> {
        val typeStrings = types.entries.map { (typeName, type) ->
            type.toGherkinStatement(typeName)
        }

        return firstLineShouldBeGiven(typeStrings)
    }
}

private fun firstLineShouldBeGiven(typeStrings: List<String>): List<String> {
    return when (typeStrings.size) {
        0 -> typeStrings
        else -> {
            val firstLine = typeStrings.first()
            val adjustedFirstLine = "Given " + firstLine.removePrefix("And ")

            listOf(adjustedFirstLine).plus(typeStrings.drop(1))
        }
    }
}
