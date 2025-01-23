package io.specmatic.core.filters

class EvalExSyntaxConverter {

    companion object {
        const val CSV_FUNCTION = "CSV"
    }

    fun standardizeExpression(expression: String): String {
        val regexPattern = "\\b\\w+(=|!=)('[^']*([,x*])[^']*')".trimIndent().toRegex()

        val result = regexPattern.replace(expression) { matchResult ->
            "$CSV_FUNCTION('${matchResult.value.filter { it != '\'' }}')"
        }
        return result
    }
}