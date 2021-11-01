package `in`.specmatic.core.log

import `in`.specmatic.core.utilities.exceptionCauseMessage

class Verbose(override val printer: CompositePrinter = CompositePrinter()) : AmountOfDetail {
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

    override fun forTheUser(e: Throwable, msg: String?) {
        printer.print(VerboseExceptionLog(e, msg))
    }

    override fun forTheUser(msg: String) {
        forTheUser(StringLog(msg))
    }

    override fun forTheUser(msg: LogMessage) {
        printer.print(msg)
    }

    override fun newLine() {
        printer.print(NewLineLogMessage)
    }

    override fun forDebugging(msg: String): String {
        forDebugging(StringLog(msg))
        return msg
    }

    override fun forDebugging(msg: LogMessage) {
        printer.print(msg)
    }

    override fun forDebugging(e: Throwable, msg: String?) {
        forTheUser(e, msg)
    }
}