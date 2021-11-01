package `in`.specmatic.core.log

class CompositePrinter(var printers: MutableList<LogPrinter> = mutableListOf(ConsolePrinter)) : LogPrinter {

    override fun print(msg: LogMessage) {
        printers.forEach { printer ->
            printer.print(msg)
        }
    }
}