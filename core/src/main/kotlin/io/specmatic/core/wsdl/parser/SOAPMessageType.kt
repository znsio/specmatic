package io.specmatic.core.wsdl.parser

enum class SOAPMessageType {
    Input {
        override val specmaticBodyType: String
            get() = "request"
    },

    Output {
        override val specmaticBodyType: String
            get() = "response"
    };

    abstract val specmaticBodyType: String
    val messageTypeName: String = this.name.lowercase()
}