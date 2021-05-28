package `in`.specmatic.core.git

import `in`.specmatic.core.utilities.exceptionCauseMessage
import `in`.specmatic.core.utilities.exceptionMessageStack

var log: Log = Info

fun logException(fn: () -> Unit) {
    try {
        fn()
    } catch(e: Throwable) {
        log.exception(e)
    }
}

interface Log {
    fun exception(e: Throwable)
    fun exception(msg: String, e: Throwable)
    fun message(msg: String)
    fun emptyLine()
    fun debug(msg: String)
}

object Info : Log {
    override fun exception(e: Throwable) {
        println(exceptionCauseMessage(e))
    }

    override fun exception(msg: String, e: Throwable) {
        println("${msg}: ${e.localizedMessage ?: e.message ?: e.javaClass.name}")
    }

    override fun message(msg: String) {
        println(msg)
    }

    override fun emptyLine() {
        println()
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

    override fun exception(msg: String, e: Throwable) {
        println("${msg}: ${e.localizedMessage ?: e.message ?: e.javaClass.name}")
        emptyLine()
        println(e.stackTraceToString())
    }

    override fun message(msg: String) {
        println(msg)
    }

    override fun emptyLine() {
        println()
    }

    override fun debug(msg: String) {
        println(msg)
    }
}