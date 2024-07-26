package io.specmatic.core.log

class TextFilePrinter(private val file: LogFile): LogPrinter {
    override fun print(msg: LogMessage) {
        file.appendText("${msg.toLogString()}\n")
    }
}