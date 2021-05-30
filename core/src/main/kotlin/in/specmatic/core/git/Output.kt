package `in`.specmatic.core.git

import `in`.specmatic.core.utilities.exceptionCauseMessage

var output: Output = Info

val log: Output
    get() {
        return output
    }

fun logException(fn: ()-> Unit): Int {
    return try {
        fn()
        0
    } catch(e: Throwable) {
        output.inform(e)
        1
    }
}

interface Output {
    fun inform(e: Throwable, msg: String? = null)
    fun inform(msg: String)
    fun newLine()
    fun debug(msg: String)
    fun debug(e: Throwable, msg: String? = null)
}

object Info : Output {
    override fun inform(e: Throwable, msg: String?) {
        when(msg) {
            null -> println(exceptionCauseMessage(e))
            else -> println("${msg}: ${exceptionCauseMessage(e)}")
        }
    }

    override fun inform(msg: String) {
        println(msg)
    }

    override fun newLine() {
        println()
    }

    override fun debug(msg: String) { }

    override fun debug(e: Throwable, msg: String?) { }
}

object Verbose : Output {
    override fun inform(e: Throwable, msg: String?) {
        when(msg) {
            null -> println(exceptionCauseMessage(e))
            else -> println("${msg}: ${e.localizedMessage ?: e.message ?: e.javaClass.name}")
        }

        newLine()
        println(e.stackTraceToString())
    }

    override fun inform(msg: String) {
        println(msg)
    }

    override fun newLine() {
        println()
    }

    override fun debug(msg: String) {
        println(msg)
    }

    override fun debug(e: Throwable, msg: String?) {
        inform(e, msg)
    }
}