package `in`.specmatic

import `in`.specmatic.core.git.log
import java.util.*

object LogTail {
    var n: Int = 5000

    private var logs = Collections.synchronizedList(LinkedList<String>())
    private var snapshot = emptyList<String>()

    @OptIn(ExperimentalStdlibApi::class)
    fun append(line: String) {
        logs.size
        logs.add(line)

        if(logs.size > n && logs.isNotEmpty()) {
            logs.removeFirst()
        }
    }

    fun append(logs: List<String>) {
        val joined = logs.joinToString(System.lineSeparator())
        append(joined)
    }

    fun storeSnapshot() {
        snapshot = logs.toList()
    }
    fun getString(): String = logs.joinToString("\n")

    fun getSnapshot(): String = snapshot.joinToString("\n")

    internal fun clear() {
        logs.clear()
    }
}

fun consoleLog(event: String) {
    LogTail.append(event)
    log.statusUpdate(event)
}

val dontPrintToConsole = { event: String ->
    LogTail.append(event)
}

val ignoreLog = { _: String -> }
