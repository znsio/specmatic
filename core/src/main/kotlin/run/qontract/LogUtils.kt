package run.qontract

import java.util.*

object LogTail {
    var n: Int = 5000

    private var logs = Collections.synchronizedList(LinkedList<String>())
    private var snapshot = emptyList<String>()

    @OptIn(ExperimentalStdlibApi::class)
    fun appendLine(line: String) {
        logs.size
        logs.add(line)

        if(logs.size > n && logs.isNotEmpty()) {
            logs.removeFirst()
        }
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
    LogTail.appendLine(event)
    println(event)
}

val nullLog = { event: String ->
    LogTail.appendLine(event)
}
