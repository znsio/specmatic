package io.specmatic.core.log

class ReadyMessage(var msg: LogMessage? = null) {
    fun printLogString(printer: LogPrinter) {
        msg?.let {
            msg = null
            printer.print(it)
        }
    }
}