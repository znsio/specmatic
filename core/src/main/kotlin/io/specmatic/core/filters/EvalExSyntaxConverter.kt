package io.specmatic.core.filters

import org.apache.commons.lang3.StringUtils

class EvalExSyntaxConverter {

    companion object {
        const val EQUALS = "="
        const val NOT_EQUALS = "!="
        const val CSV_FUNCTION = "CSV"
    }

    fun standardizeExpression(expression: String): String {
        val regex = Regex("""
        [A-Za-z]+(?:=|!=)[^()\s&|]+(?:\([^()]*\))?|  
        \(|
        \)|
        !(?=\()|
        &&|
        \|\|
        """.trimIndent().replace(Regex("#.*\\n"), "").replace("\\s+".toRegex(), ""))

        val tokens = regex.findAll(expression).map { it.value.trim() }.filter { it.isNotEmpty() }.toList()

        return tokens.joinToString(" ") { token ->
            when {
                token.contains(EQUALS) && !token.contains(NOT_EQUALS) -> {
                    val (key, value) = token.split(EQUALS).let { it[0] to it[1] }
                    when {
                        requiresCsvFunction(value) -> "$CSV_FUNCTION(\"$key$EQUALS$value\")"
                        key.contains(ScenarioFilterTags.STATUS_CODE.key) -> {"$key=$value"}
                        else -> "$key=\"$value\""
                    }
                }
                token.contains(NOT_EQUALS) -> {
                    val (key, value) = token.split(NOT_EQUALS).let { it[0] to it[1] }
                    when {
                        requiresCsvFunction(value) -> "$CSV_FUNCTION(\"$key$NOT_EQUALS$value\")"
                        key.contains(ScenarioFilterTags.STATUS_CODE.key) -> {"$key!=$value"}
                        else -> "$key!=\"$value\""
                    }
                }
                else -> token
            }
        }
    }
    private fun requiresCsvFunction(value: String): Boolean {
        return value.contains(',') || value.endsWith('x') || value.endsWith("xx") || value.contains("*")
    }
}