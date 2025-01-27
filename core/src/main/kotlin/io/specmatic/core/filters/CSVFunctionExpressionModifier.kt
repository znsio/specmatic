package io.specmatic.core.filters

class CSVFunctionExpressionModifier {
    fun standardizeExpression(expression: String): String {
        val regexPattern = "\\b\\w+(=|!=)('[^']*([,x*])[^']*')".trimIndent().toRegex()

        return regexPattern.replace(expression) { matchResult ->
            "CSV('${matchResult.value.filter { it != '\'' }}')"
        }
    }
}