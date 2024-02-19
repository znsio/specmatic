package `in`.specmatic.core.log

var logger: LogStrategy = newLogger()
fun newLogger(): LogStrategy = ThreadSafeLog(NonVerbose(CompositePrinter()))
fun resetLogger() {
    logger = NonVerbose(CompositePrinter())
}

fun logException(fn: ()-> Unit): Int {
    return try {
        fn()
        0
    } catch(e: Throwable) {
        logger.log(e)
        1
    }
}

fun consoleLog(event: String) {
    consoleLog(StringLog(event))
}

fun consoleLog(event: LogMessage) {
    LogTail.append(event)
    logger.log(event)
}

fun consoleLog(e: Throwable) {
    LogTail.append(logger.ofTheException(e))
    logger.log(e)
}

fun consoleLog(e: Throwable, msg: String) {
    LogTail.append(logger.ofTheException(e, msg))
    logger.log(e, msg)
}

val dontPrintToConsole = { event: LogMessage ->
    LogTail.append(event)
}

val ignoreLog = { _: LogMessage -> }
