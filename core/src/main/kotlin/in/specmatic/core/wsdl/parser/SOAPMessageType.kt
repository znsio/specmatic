package `in`.specmatic.core.wsdl.parser

enum class SOAPMessageType {
    Input {
        override val qontractBodyType: String
            get() = "request"
    },

    Output {
        override val qontractBodyType: String
            get() = "response"
    };

    abstract val qontractBodyType: String
    val messageTypeName: String = this.name.lowercase()
}