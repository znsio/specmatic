package io.specmatic.core.log

open class CompositePrinter(private var printers: List<LogPrinter> = listOf(ConsolePrinter)) : LogPrinter {
    override fun print(msg: LogMessage, indentation: String) {
        printers.forEach { printer ->
            printer.print(msg, indentation)
        }
    }
}