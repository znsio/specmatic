package io.specmatic.core

data class Link(
    val operationId: String? = null,
    val operationRef: String? = null,
    val parameters: Map<String, String> = emptyMap(),
    val requestBody: String? = null,
    val description: String? = null,
    val server: LinkServer? = null
) {
    companion object {

        fun isExpression(value: String): Boolean {
            return when {
                value.startsWith("$") -> true
                value.contains(Regex("\\{\\$.*?}")) -> true
                else -> false
            }
        }

        fun extractExpressions(value: String): List<String> {
            val expressions = mutableListOf<String>()

            // Check for pure runtime expression
            if (value.startsWith("$")) {
                expressions.add(value)
                return expressions
            }

            // Extract embedded expressions
            val regex = Regex("\\{(\\$.*?)}")
            regex.findAll(value).forEach {
                expressions.add(it.groupValues[1])
            }

            return expressions
        }
    }
}

data class LinkServer(
    val url: String,
    val description: String? = null
) {
    init {
        require(url.isNotBlank()) { "Server URL cannot be blank" }

        // Ensure URL is properly formatted
        require(isValidUrl(url)) {
            "Invalid server URL format: $url"
        }
    }

    companion object {
        private fun isValidUrl(url: String): Boolean {
            // URL can contain variables in {variableName} format
            // This regex allows standard URLs and URLs with variables
            val urlPattern = Regex("^(https?://|/).*|\\{[^{}]+}.*$")
            return url.matches(urlPattern)
        }
    }
}

