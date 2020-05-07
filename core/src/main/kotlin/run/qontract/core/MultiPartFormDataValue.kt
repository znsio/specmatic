package run.qontract.core

import run.qontract.core.value.Value

sealed class MultiPartFormDataValue(open val name: String) {
    abstract fun toPattern(): MultiPartFormDataPattern
    abstract fun toDisplayableValue(): String
}

data class MultiPartContentValue(override val name: String, val content: Value, val boundary: String = "#####") : MultiPartFormDataValue(name) {
    override fun toPattern(): MultiPartFormDataPattern {
        return MultiPartContentPattern(name, content.toPattern())
    }

    override fun toDisplayableValue(): String = """
--$boundary
Content-Disposition: form-data; name="$name"
Content-Type: ${content.httpContentType}

$content
""".trim()
}

data class MultiPartFileValue(override val name: String, val filename: String, val contentType: String, val contentEncoding: String? = null, val content: String? = null, val boundary: String = "#####") : MultiPartFormDataValue(name) {
    override fun toPattern(): MultiPartFormDataPattern {
        return MultiPartFilePattern(name, filename, contentType, contentEncoding)
    }

    override fun toDisplayableValue(): String {
        val headers =
                listOf("""Content-Disposition: form-data; name="$name"; filename="$filename"""", """Content-Type: $contentType""")
        val headersWithEncoding = contentEncoding?.let {
            headers.plus("Content-Encoding: $contentEncoding")
        } ?: headers

        val headerString = headersWithEncoding.joinToString(System.lineSeparator())

        return """
--$boundary
$headerString

$content
""".trim()
    }
}
