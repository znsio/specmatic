package `in`.specmatic.core.log

open class CompositePrinter(var printers: List<LogPrinter> = listOf(ConsolePrinter)) : LogPrinter {
    override fun print(msg: LogMessage) {
        printers.forEach { printer ->
            printer.print(msg)
        }
    }
}