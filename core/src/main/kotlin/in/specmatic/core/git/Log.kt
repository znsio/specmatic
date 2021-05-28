package `in`.specmatic.core.git

import `in`.specmatic.core.utilities.exceptionCauseMessage

var log: Log = Info

interface Log {
    fun exception(e: Throwable)
    fun message(msg: String)
    fun debug(msg: String)
}

object Info : Log {
    override fun exception(e: Throwable) {
        println(exceptionCauseMessage(e))
    }

    override fun message(msg: String) {
        println(msg)
    }

    override fun debug(msg: String) {
    }
}

object Verbose : Log {
    override fun exception(e: Throwable) {
        println(exceptionCauseMessage(e))
        println()
        println(e.stackTraceToString())
    }

    override fun message(msg: String) {
        println(msg)
    }

    override fun debug(msg: String) {
        println(msg)
    }
}