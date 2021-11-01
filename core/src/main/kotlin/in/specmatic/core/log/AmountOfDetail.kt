package `in`.specmatic.core.log

interface AmountOfDetail {
    val printer: CompositePrinter

    fun exceptionString(e: Throwable, msg: String? = null): String
    fun ofTheException(e: Throwable, msg: String? = null): LogMessage
    fun forTheUser(e: Throwable, msg: String? = null)
    fun forTheUser(msg: String)
    fun forTheUser(msg: LogMessage)

    fun newLine()
    fun forDebugging(msg: String): String
    fun forDebugging(msg: LogMessage)
    fun forDebugging(e: Throwable, msg: String? = null)
}