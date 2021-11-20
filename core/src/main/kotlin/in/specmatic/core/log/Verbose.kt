package `in`.specmatic.core.log

import `in`.specmatic.core.utilities.exceptionCauseMessage

class Verbose(override val printer: CompositePrinter = CompositePrinter()) : LogStrategy {
    private val readyMessage = ReadyMessage()

    override fun keepReady(msg: LogMessage) {
        readyMessage.msg = msg
    }

    fun print(msg: LogMessage) {
        readyMessage.printLogString(printer)
        printer.print(msg)
    }

    override fun exceptionString(e: Throwable, msg: String?): String {
        val message = when(msg) {
            null -> exceptionCauseMessage(e)
            else -> "${msg}: ${e.localizedMessage ?: e.message ?: e.javaClass.name}"
        }

        return "$message${System.lineSeparator()}${e.stackTraceToString()}"
    }

    override fun ofTheException(e: Throwable, msg: String?): LogMessage {
        return VerboseExceptionLog(e, msg)
    }

    override fun log(e: Throwable, msg: String?) {
        print(VerboseExceptionLog(e, msg))
    }

    override fun log(msg: String) {
        log(StringLog(msg))
    }

    override fun log(msg: LogMessage) {
        print(msg)
    }

    override fun newLine() {
        print(NewLineLogMessage)
    }

    override fun debug(msg: String): String {
        debug(StringLog(msg))
        return msg
    }

    override fun debug(msg: LogMessage) {
        print(msg)
    }

    override fun debug(e: Throwable, msg: String?) {
        log(e, msg)
    }
}