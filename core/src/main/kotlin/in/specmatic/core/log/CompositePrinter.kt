package `in`.specmatic.core.log

class CompositePrinter: LogPrinter {
    var printers: MutableList<LogPrinter> = mutableListOf(ConsolePrinter)

    override fun print(msg: LogMessage) {
        printers.forEach { printer ->
            printer.print(msg)
        }
    }
}