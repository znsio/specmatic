package run.qontract.core.pattern

enum class URLScheme(val schemeName: String) {
    HTTP("http") {
        override val prefix = "$schemeName://"
    },
    HTTPS("https") {
        override val prefix = "$schemeName://"
    },
    PATH("") {
        override val prefix = "/"
    };

    abstract val prefix: String
}