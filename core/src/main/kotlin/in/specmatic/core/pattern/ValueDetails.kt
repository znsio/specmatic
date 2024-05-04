package `in`.specmatic.core.pattern

class ValueDetails(val messages: List<String> = emptyList(), val breadCrumbs: List<String> = emptyList()) {
    fun addDetails(message: String, breadCrumb: String): ValueDetails {
        return ValueDetails(
            messages.addNonBlank(message),
            breadCrumbs.addNonBlank(breadCrumb)
        )
    }

    fun comments(): String? {
        if(messages.isEmpty())
            return null

        val breadCrumbs = breadCrumbs.reversed().joinToString(".")
        val body = messages.joinToString(System.lineSeparator())

        return """
>> $breadCrumbs

   $body
        """.trimIndent()
    }

    private fun List<String>.addNonBlank(
        errorMessage: String
    ) = if (errorMessage.isNotBlank())
        this.plus(errorMessage)
    else
        this
}