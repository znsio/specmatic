package run.qontract

import java.util.*

object LogTail {
    var n: Int = 5000

    private var logs = LinkedList<String>()
    private var lastLoadSnapshot = emptyList<String>()

    fun appendLine(line: String) {
        logs.size
        logs.add(line)

        if(logs.size > n && logs.isNotEmpty()) {
            logs.removeFirst()
        }
    }

    fun storeLastLoadSnapshot() {
        lastLoadSnapshot = logs.toList()
    }

    fun getString(): String = logs.joinToString("\n")
    fun getLoadLogString(): String = lastLoadSnapshot.joinToString("\n")
}

fun consoleLog(event: String) {
    LogTail.appendLine(event)
    println(event)
}

val nullLog = { event: String ->
    LogTail.appendLine(event)
}
