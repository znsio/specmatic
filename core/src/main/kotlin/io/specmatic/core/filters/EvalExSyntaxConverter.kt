package io.specmatic.core.filters

class EvalExSyntaxConverter() {

    fun standardizeExpression(expression: String): String {
        return expression
            .let(::escapePaths)
            .let(::convertEqualsToDoubleEquals)
            .let(::expandStatusRanges)
            .let(::encloseValues)
            .let(::handleMultipleValues)
//            .let(::handleStatusRange)
    }
    private fun escapePaths(expression: String): String {
        return expression.split(" ").joinToString(" ") { part ->
            if (part.startsWith("/") && !part.startsWith("\"")) "\"$part\"" else part
        }
    }
    private fun convertEqualsToDoubleEquals(expression: String): String {
        return expression.replace("=", "==").replace("!==", "!=")
    }

    private fun encloseValues(expression: String): String {
        val operators = listOf("==", "!=")
        var standardized = expression
        for (operator in operators) {
            var index = standardized.indexOf(operator)
            while (index != -1) {
                val keyStart = standardized.lastIndexOf(' ', index - 1) + 1
                val keyEnd = index
                val key = standardized.substring(keyStart, keyEnd).trim()

                val valueStart = index + operator.length
                val valueEnd = standardized.indexOfAny(charArrayOf(' ', '&', '|', '(', ')'), valueStart).takeIf { it != -1 }
                    ?: standardized.length
                val value = standardized.substring(valueStart, valueEnd).trim()

                val newValue = if (key == "STATUS") value
                else if (value.contains(',')) {
                    value.split(',').joinToString(",") { "\"$it\"" }
                } else "\"$value\""
                standardized = standardized.substring(0, valueStart) + newValue + standardized.substring(valueEnd)
                index = standardized.indexOf(operator, valueEnd + newValue.length - value.length)
            }
        }

        return standardized
    }

    private fun handleMultipleValues(expression: String): String {
        val operators = listOf("==", "!=")
        var standardized = expression

        val keys = listOf("STATUS", "PATH", "METHOD")

        for (key in keys) {
            for (operator in operators) {
                var index = standardized.indexOf("$key$operator")
                while (index != -1) {
                    val valueStart = index + "$key$operator".length
                    val valueEnd = standardized.indexOfAny(charArrayOf(' ', '&', '|', '(', ')'), valueStart).takeIf { it != -1 }
                        ?: standardized.length
                    val values = standardized.substring(valueStart, valueEnd).trim().split(",")

                    val newCondition = if (values.size > 1) {
                        values.joinToString(" ${if (operator == "==") "||" else "&&"} ") {
                            "$key$operator$it"
                        }.let { "($it)" }
                    } else {
                        "$key$operator${values.first()}"
                    }

                    standardized = standardized.substring(0, index) + newCondition + standardized.substring(valueEnd)
                    index = standardized.indexOf("$key$operator", index + newCondition.length)
                }
            }
        }

        return standardized
    }

    private fun expandStatusRanges(expression: String): String {
        val statusPattern = "STATUS"
        val operators = listOf("==", "!=")
        var result = expression

        for (operator in operators) {
            val statusPatternWithOperator = "$statusPattern$operator"
            if (result.startsWith(statusPatternWithOperator)) {
                val statuses = result.removePrefix(statusPatternWithOperator).split(",")
                val expandedStatuses = mutableListOf<String>()

                for (status in statuses) {
                    when {
                        status.endsWith("xx") -> {
                            val prefix = status.dropLast(2)
                            for (i in 0..99) {
                                expandedStatuses.add(prefix + String.format("%02d", i))
                            }
                        }
                        status.endsWith("x") -> {
                            val prefix = status.dropLast(1)
                            for (i in 0..9) {
                                expandedStatuses.add(prefix + i)
                            }
                        }
                        else -> {
                            expandedStatuses.add(status)
                        }
                    }
                }
                result = statusPatternWithOperator + expandedStatuses.joinToString(",")
                break
            }
        }

        return result
    }

}
