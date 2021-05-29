package `in`.specmatic.core.git

import `in`.specmatic.core.utilities.exceptionCauseMessage

var log: Log = Info

fun logException(fn: ()-> Unit): Int {
    return try {
        fn()
        0
    } catch(e: Throwable) {
        log.statusUpdate(e)
        1
    }
}

interface Log {
    fun statusUpdate(e: Throwable, msg: String? = null)
    fun statusUpdate(msg: String)
    fun statusUpdate()
    fun debug(msg: String)
    fun debug(e: Throwable, msg: String? = null)
}

object Info : Log {
    override fun statusUpdate(e: Throwable, msg: String?) {
        when(msg) {
            null -> println(exceptionCauseMessage(e))
            else -> println("${msg}: ${exceptionCauseMessage(e)}")
        }
    }

    override fun statusUpdate(msg: String) {
        println(msg)
    }

    override fun statusUpdate() {
        println()
    }

    override fun debug(msg: String) { }

    override fun debug(e: Throwable, msg: String?) { }
}

object Verbose : Log {
    override fun statusUpdate(e: Throwable, msg: String?) {
        when(msg) {
            null -> println(exceptionCauseMessage(e))
            else -> println("${msg}: ${e.localizedMessage ?: e.message ?: e.javaClass.name}")
        }

        statusUpdate()
        println(e.stackTraceToString())
    }

    override fun statusUpdate(msg: String) {
        println(msg)
    }

    override fun statusUpdate() {
        println()
    }

    override fun debug(msg: String) {
        println(msg)
    }

    override fun debug(e: Throwable, msg: String?) {
        statusUpdate(e, msg)
    }
}