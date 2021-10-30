package `in`.specmatic.core.log

var details: AmountOfDetail = NonVerbose

fun logException(fn: ()-> Unit): Int {
    return try {
        fn()
        0
    } catch(e: Throwable) {
        details.forTheUser(e)
        1
    }
}

val logPrinter = CompositePrinter()

fun consoleLog(event: LogMessage) {
    LogTail.append(event)
    details.forTheUser(event)
}

fun consoleLog(e: Throwable) {
    LogTail.append(details.ofTheException(e))
    details.forTheUser(e)
}

fun consoleLog(e: Throwable, msg: String) {
    LogTail.append(details.ofTheException(e, msg))
    details.forTheUser(e, msg)
}

val dontPrintToConsole = { event: LogMessage ->
    LogTail.append(event)
}

val ignoreLog = { _: LogMessage -> }
