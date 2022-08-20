package `in`.specmatic.core.log

class CompositePrinter(var printers: List<LogPrinter> = listOf(ConsolePrinter)) : LogPrinter {
    override fun print(msg: LogMessage) {
        printers.forEach { printer ->
            printer.print(msg)
        }
    }
}