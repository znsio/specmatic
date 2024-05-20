package `in`.specmatic.core.pattern

class ValueDetails(val messages: List<String> = emptyList(), private val breadCrumbData: List<String> = emptyList()) {
    fun addDetails(message: String, breadCrumb: String): ValueDetails {
        return ValueDetails(
            messages.addNonBlank(message),
            breadCrumbData.addNonBlank(breadCrumb)
        )
    }

    val breadCrumbs: String
        get() {
            return breadCrumbData.reversed().joinToString(".")
        }

    fun comments(): String? {
        if(messages.isEmpty())
            return null

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