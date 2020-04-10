package run.qontract.core.pattern

import run.qontract.core.value.StringValue

enum class URLScheme(val schemes: List<String>) {
    HTTP(listOf("http")) {
        override val prefix = "${schemes.first()}://"
    },
    HTTPS(listOf("https")) {
        override val prefix = "${schemes.first()}://"
    },
    PATH(listOf("/")) {
        override val prefix = "/"
    },
    EITHER(listOf("http", "https")) {
        override val prefix = "${schemes.first()}://"
    };

    abstract val prefix: String
    fun matches(url: StringValue): Boolean = schemes.any { url.string.startsWith(it) }
}