package run.qontract.core.pattern

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
}