package io.specmatic.core.log

object OneLinePrinter: LogPrinter {
    override fun print(msg: LogMessage, indentation: String) {
        println(msg.toOneLineString().prependIndent(indentation))
    }
}