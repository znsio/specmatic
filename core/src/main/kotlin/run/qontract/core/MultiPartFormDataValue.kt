package run.qontract.core

import run.qontract.core.value.Value

sealed class MultiPartFormDataValue(open val name: String) {
    abstract fun toPattern(): MultiPartFormDataPattern
    abstract fun toDisplayableValue(): String
}

data class MultiPartContentValue(override val name: String, val content: Value) : MultiPartFormDataValue(name) {
    override fun toPattern(): MultiPartFormDataPattern {
        return MultiPartContentPattern(name, content.toPattern())
    }

    override fun toDisplayableValue(): String = "Name: $name, Content: ${content.toStringValue()}"
}

data class MultiPartFileValue(override val name: String, val filename: String, val contentType: String, val contentEncoding: String?) : MultiPartFormDataValue(name) {
    override fun toPattern(): MultiPartFormDataPattern {
        return MultiPartFilePattern(name, filename, contentType, contentEncoding)
    }

    override fun toDisplayableValue(): String =
            "Name: $name, Filename: $filename, ContentType: $contentType, ContentEncoding: $contentEncoding"
}
