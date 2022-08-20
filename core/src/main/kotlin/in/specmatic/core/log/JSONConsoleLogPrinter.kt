package `in`.specmatic.core.log

object JSONConsoleLogPrinter: LogPrinter {
    override fun print(msg: LogMessage) {
        println(msg.toJSONObject().toStringLiteral())
    }
}