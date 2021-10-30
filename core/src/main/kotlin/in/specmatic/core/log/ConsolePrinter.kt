package `in`.specmatic.core.log

object ConsolePrinter: LogPrinter {
    override fun print(msg: LogMessage) {
        println(msg.toLogString())
    }
}