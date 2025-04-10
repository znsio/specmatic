package io.specmatic.core.log

class TextFilePrinter(private val file: LogFile): LogPrinter {
    override fun print(msg: LogMessage, indentation: String) {
        file.appendText("${msg.toLogString().prependIndent(indentation)}\n")
    }
}