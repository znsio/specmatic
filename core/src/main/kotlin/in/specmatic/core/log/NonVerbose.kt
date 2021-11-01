package `in`.specmatic.core.log

import `in`.specmatic.core.utilities.exceptionCauseMessage

class NonVerbose(override val printer: CompositePrinter) : AmountOfDetail {
    override fun exceptionString(e: Throwable, msg: String?): String {
        return when(msg) {
            null -> exceptionCauseMessage(e)
            else -> "${msg}: ${exceptionCauseMessage(e)}"
        }
    }

    override fun ofTheException(e: Throwable, msg: String?): LogMessage {
        return NonVerboseExceptionLog(e, msg)
    }

    override fun forTheUser(e: Throwable, msg: String?) {
        printer.print(NonVerboseExceptionLog(e, msg))
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

    override fun forDebugging(msg: String): String { return msg }
    override fun forDebugging(msg: LogMessage) {

    }

    override fun forDebugging(e: Throwable, msg: String?) { }
}