package `in`.specmatic.core

import `in`.specmatic.core.utilities.exceptionCauseMessage

var information: Output = Info

fun logException(fn: ()-> Unit): Int {
    return try {
        fn()
        0
    } catch(e: Throwable) {
        information.forTheUser(e)
        1
    }
}

interface Output {
    fun exceptionString(e: Throwable, msg: String? = null): String
    fun forTheUser(e: Throwable, msg: String? = null)
    fun forTheUser(msg: String)
    fun newLine()
    fun forDebugging(msg: String): String
    fun forDebugging(e: Throwable, msg: String? = null)
}

object Info : Output {
    override fun exceptionString(e: Throwable, msg: String?): String {
        return when(msg) {
            null -> exceptionCauseMessage(e)
            else -> "${msg}: ${exceptionCauseMessage(e)}"
        }
    }

    override fun forTheUser(e: Throwable, msg: String?) {
        println(exceptionString(e, msg))
    }

    override fun forTheUser(msg: String) {
        println(msg)
    }

    override fun newLine() {
        println()
    }

    override fun forDebugging(msg: String): String { return msg }

    override fun forDebugging(e: Throwable, msg: String?) { }
}

object Verbose : Output {
    override fun exceptionString(e: Throwable, msg: String?): String {
        val message = when(msg) {
            null -> exceptionCauseMessage(e)
            else -> "${msg}: ${e.localizedMessage ?: e.message ?: e.javaClass.name}"
        }

        return "$message${System.lineSeparator()}${e.stackTraceToString()}"
    }

    override fun forTheUser(e: Throwable, msg: String?) {
        println(exceptionString(e, msg))
    }

    override fun forTheUser(msg: String) {
        println(msg)
    }

    override fun newLine() {
        println()
    }

    override fun forDebugging(msg: String): String {
        println(msg)
        return msg
    }

    override fun forDebugging(e: Throwable, msg: String?) {
        forTheUser(e, msg)
    }
}