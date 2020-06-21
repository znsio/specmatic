package run.qontract

import java.util.*

internal object LogTail {
    var n: Int = 5000

    private var logs = LinkedList<String>()

    fun appendLine(line: String) {
        logs.size
        logs.add(line)

        if(logs.size > n && logs.isNotEmpty()) {
            logs.removeFirst()
        }
    }

    fun getString(): String = logs.joinToString("\n")
}

fun consoleLog(event: String) {
    LogTail.appendLine(event)
    println(event)
}

val nullLog = { event: String ->
    LogTail.appendLine(event)
}
