package `in`.specmatic.core.pattern

import `in`.specmatic.core.value.StringValue

enum class URLScheme(val schemes: List<String>) {
    HTTP(listOf("http")) {
        override val prefix = "${schemes.first()}://"
        override val type: String = "an http url"
    },
    HTTPS(listOf("https")) {
        override val prefix = "${schemes.first()}://"
        override val type: String = "an https url"
    },
    PATH(emptyList()) {
        override val prefix = ""
        override val type: String = "a url path"

        override fun matches(url: StringValue): Boolean = true
        override val tld: String = ""
    },
    EITHER(listOf("http", "https")) {
        override val prefix = "${schemes.first()}://"
        override val type: String = "either an http or or an https url"
    };

    abstract val prefix: String
    abstract val type: String
    open val tld: String = ".com"
    open fun matches(url: StringValue): Boolean = schemes.any { url.string.startsWith(it) }
}