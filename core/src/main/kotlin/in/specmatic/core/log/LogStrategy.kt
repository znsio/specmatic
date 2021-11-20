package `in`.specmatic.core.log

interface LogStrategy {
    val printer: CompositePrinter

    fun keepReady(msg: LogMessage)

    fun exceptionString(e: Throwable, msg: String? = null): String
    fun ofTheException(e: Throwable, msg: String? = null): LogMessage
    fun log(e: Throwable, msg: String? = null)
    fun log(msg: String)
    fun log(msg: LogMessage)

    fun newLine()
    fun debug(msg: String): String
    fun debug(msg: LogMessage)
    fun debug(e: Throwable, msg: String? = null)
}