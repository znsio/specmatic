package io.specmatic.core.log

object ConsolePrinter: LogPrinter {
    override fun print(msg: LogMessage, indentation: String) {
        println(msg.toLogString().prependIndent(indentation))
    }
}