package io.specmatic.core.log

class JSONFilePrinter(private val file: LogFile): LogPrinter {
    override fun print(msg: LogMessage) {
        file.appendText("${msg.toJSONObject()}\n")
    }
}