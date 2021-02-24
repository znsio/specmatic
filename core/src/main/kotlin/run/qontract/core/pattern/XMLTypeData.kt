package run.qontract.core.pattern

import run.qontract.core.value.StringValue
import run.qontract.core.value.XMLNode

data class XMLTypeData(val name: String = "", val realName: String, val attributes: Map<String, Pattern> = emptyMap(), val nodes: List<Pattern> = emptyList()) {
    fun isEmpty(): Boolean {
        return name.isEmpty() && attributes.isEmpty() && nodes.isEmpty()
    }

    fun toGherkinString(additionalIndent: String = "", indent: String = ""): String {
        val attributeText = attributes.entries.joinToString(" ") { (key, value) -> "$key=\"$value\"" }.let { if(it.isNotEmpty()) " $it" else ""}

        return when {
            nodes.isEmpty() -> {
                return "$indent<$realName$attributeText/>"
            }
            nodes.size == 1 && nodes.first() !is XMLPattern -> {
                val bodyText = nodes.first().pattern.toString()
                "$indent<$realName$attributeText>$bodyText</$realName>"
            }
            else -> {
                val childNodeText = nodes.joinToString("\n") {
                    if(it !is XMLPattern)
                        throw ContractException("Expected an xml node: $it")

                    it.toGherkinString(additionalIndent, indent + additionalIndent)
                }

                "$indent<$realName$attributeText>\n$childNodeText\n$indent</$realName>"
            }
        }
    }

    fun toGherkinishNode(): XMLNode {
        val childXMLNodes = nodes.map {
            when(it) {
                is XMLPattern -> it.toGherkinXMLNode()
                else -> StringValue(it.pattern.toString())
            }
        }

        return XMLNode(realName, attributes.mapValues { StringValue(it.value.pattern.toString()) }, childXMLNodes)
    }

    fun isOptionalNode(): Boolean {
        return attributes["qontract_optional"].let {
            it is ExactValuePattern && it.pattern.toStringValue() == "true"
        }
    }

    fun isMultipleNode(): Boolean {
        return attributes["qontract_multiple"].let {
            it is ExactValuePattern && it.pattern.toStringValue() == "true"
        }
    }
}