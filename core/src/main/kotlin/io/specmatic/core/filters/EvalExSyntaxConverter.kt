package io.specmatic.core.filters

import org.apache.commons.lang3.StringUtils
import java.util.*

class EvalExSyntaxConverter {

    companion object {
        const val EQUALS = "="
        const val NOT_EQUALS = "!="
        const val CSV_FUNCTION = "CSV"
    }

    fun standardizeExpression(expression: String): String {
        val tokenizer = StringTokenizer(expression, " ()=!", true)
        val tokens = mutableListOf<String>()

        while (tokenizer.hasMoreTokens()) {
            val token = tokenizer.nextToken().trim()
            if (token.isNotEmpty()) {
                tokens.add(token)
            }
        }
        return StringUtils.splitByWholeSeparatorPreserveAllTokens(expression, " ")
            .joinToString(" ") { token ->
                when {
                    token.contains(EQUALS) -> {
                        val (key, value) = token.split(EQUALS)
                        if (value.contains(',') || value.endsWith('x') || value.endsWith("xx")) "$CSV_FUNCTION(\"$key$EQUALS$value\")"
                        else if(key.contains(ScenarioFilterTags.STATUS_CODE.key)) {
                            "$key=$value"
                        }
                        else "$key=\"$value\""
                    }
                    token.contains(NOT_EQUALS) -> {
                        val (key, value) = token.split(NOT_EQUALS)
                        if (value.contains(",") || value.endsWith('x') || value.endsWith("xx")) "$CSV_FUNCTION(\"$key$NOT_EQUALS$value\")"
                        else if(key.contains(ScenarioFilterTags.STATUS_CODE.key))
                            "$key!=$value"
                        else "$key!=\"$value\""
                    }
                    else -> token
                }
            }
    }
}