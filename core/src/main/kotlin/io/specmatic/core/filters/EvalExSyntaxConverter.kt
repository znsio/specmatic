package io.specmatic.core.filters

import org.apache.commons.lang3.StringUtils

class EvalExSyntaxConverter {

    companion object {
        const val CSV_FUNCTION = "CSV"
    }

    fun standardizeExpression(expression: String): String {
        val regexPattern = "\\b\\w+(=|!=)('[^']*([,x*])[^']*'|[^|&(]*([,x*])[^|&()]*\\b)".trimIndent().toRegex()

        val result = regexPattern.replace(expression) { matchResult ->
            "$CSV_FUNCTION('${matchResult.value.filter { it != '\'' }}')"
        }
        return result
    }
}