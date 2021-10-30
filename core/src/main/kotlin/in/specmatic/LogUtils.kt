package `in`.specmatic

import `in`.specmatic.core.LogMessage
import `in`.specmatic.core.details
import java.util.*

object LogTail {
    var n: Int = 5000

    private var logs = Collections.synchronizedList(LinkedList<LogMessage>())
    private var snapshot = emptyList<LogMessage>()

    @OptIn(ExperimentalStdlibApi::class)
    fun append(msg: LogMessage) {
        logs.size
        logs.add(msg)

        if(logs.size > n && logs.isNotEmpty()) {
            logs.removeFirst()
        }
    }

    fun storeSnapshot() {
        snapshot = logs.toList()
    }
    fun getString(): String = logs.joinToString("\n") { it.toLogString() }

    fun getSnapshot(): String = snapshot.joinToString("\n") { it.toLogString() }

    internal fun clear() {
        logs.clear()
    }
}

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
