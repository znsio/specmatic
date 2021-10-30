package `in`.specmatic.core

import `in`.specmatic.core.utilities.exceptionCauseMessage
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value
import java.io.File
import java.util.*

var details: AmountOfDetail = NonVerbose

fun logException(fn: ()-> Unit): Int {
    return try {
        fn()
        0
    } catch(e: Throwable) {
        details.forTheUser(e)
        1
    }
}

class StringLog(private val msg: String): LogMessage {
    override fun toJSONObject(): JSONObjectValue {
        return JSONObjectValue(mapOf("message" to StringValue(msg)))
    }

    override fun toLogString(): String {
        return msg
    }
}

private class VerboseExceptionLog(private val e: Throwable, val msg: String?): LogMessage {
    override fun toJSONObject(): JSONObjectValue {
        val data: Map<String, Value> = mapOf(
            "className" to StringValue(e.javaClass.name),
            "cause" to StringValue(exceptionCauseMessage(e)),
            "stackTrace" to StringValue(e.stackTraceToString())
        )

        val message: Map<String, Value> = msg?.let {
            mapOf("message" to StringValue(msg))
        } ?: emptyMap()

        return JSONObjectValue(data.plus(message))
    }

    override fun toLogString(): String {
        val message = when(msg) {
            null -> exceptionCauseMessage(e)
            else -> "${msg}: ${e.localizedMessage ?: e.message ?: e.javaClass.name}"
        }

        return "$message${System.lineSeparator()}${e.stackTraceToString()}"
    }
}

private class NonVerboseExceptionLog(private val e: Throwable, val msg: String?): LogMessage {
    override fun toJSONObject(): JSONObjectValue {
        val data: Map<String, Value> = mapOf(
            "className" to StringValue(e.javaClass.name),
            "cause" to StringValue(exceptionCauseMessage(e))
        )

        val message: Map<String, Value> = msg?.let {
            mapOf("message" to StringValue(msg))
        } ?: emptyMap()

        return JSONObjectValue(data.plus(message))
    }

    override fun toLogString(): String {
        return when(msg) {
            null -> exceptionCauseMessage(e)
            else -> "${msg}: ${exceptionCauseMessage(e)}"
        }
    }
}

interface LogMessage {
    fun toJSONObject(): JSONObjectValue
    fun toLogString(): String
}

private object NewLineLogMessage: LogMessage {
    override fun toJSONObject(): JSONObjectValue {
        return JSONObjectValue()
    }

    override fun toLogString(): String {
        return "\n"
    }
}

interface AmountOfDetail {
    fun exceptionString(e: Throwable, msg: String? = null): String
    fun ofTheException(e: Throwable, msg: String? = null): LogMessage
    fun forTheUser(e: Throwable, msg: String? = null)
    fun forTheUser(msg: String)
    fun forTheUser(msg: LogMessage)

    fun newLine()
    fun forDebugging(msg: String): String
    fun forDebugging(msg: LogMessage)
    fun forDebugging(e: Throwable, msg: String? = null)
}

interface LogPrinter {
    fun print(msg: LogMessage)
}

interface LogFile {
    fun appendText(text: String)
}

fun logFileNameSuffix(tag: String, extension: String): String {
    return tag.let {
        if(it.isNotBlank()) "-$it" else ""
    } + extension.let {
        if(it.isNotBlank()) ".$it" else ""
    }
}

class LogDirectory(directory: File, prefix: String, tag: String, extension: String): LogFile {
    constructor(directory: String, prefix: String, tag: String, extension: String): this(File(directory), prefix, tag, extension)

    val file: File

    init {
        if(!directory.exists())
            directory.mkdirs()

        val calendar = Calendar.getInstance()

        val parts: List<String> = listOf(
            Calendar.YEAR,
            Calendar.MONTH,
            Calendar.DAY_OF_MONTH,
            Calendar.HOUR,
            Calendar.MINUTE,
            Calendar.SECOND
        ).map {
            calendar.get(it).toString()
        }

        val name = "$prefix-${parts.joinToString("-")}${logFileNameSuffix(tag, extension)}"

        file = directory.resolve(name)
        if(!file.exists())
            file.createNewFile()
    }

    override fun appendText(text: String) {
        file.appendText(text)
    }
}

object JSONConsoleLogPrinter: LogPrinter {
    override fun print(msg: LogMessage) {
        println(msg.toJSONObject())
    }
}

class JSONFilePrinter(private val file: LogFile): LogPrinter {
    override fun print(msg: LogMessage) {
        file.appendText("${msg.toJSONObject()}\n")
    }
}

class TextFilePrinter(private val file: LogFile): LogPrinter {
    override fun print(msg: LogMessage) {
        file.appendText("${msg.toLogString()}\n")
    }
}

object ConsolePrinter: LogPrinter {
    override fun print(msg: LogMessage) {
        println(msg.toLogString())
    }
}

class CompositePrinter: LogPrinter {
    var printers: MutableList<LogPrinter> = mutableListOf(ConsolePrinter)

    override fun print(msg: LogMessage) {
        printers.forEach { printer ->
            printer.print(msg)
        }
    }
}

val logPrinter = CompositePrinter()

object NonVerbose : AmountOfDetail {
    override fun exceptionString(e: Throwable, msg: String?): String {
        return when(msg) {
            null -> exceptionCauseMessage(e)
            else -> "${msg}: ${exceptionCauseMessage(e)}"
        }
    }

    override fun ofTheException(e: Throwable, msg: String?): LogMessage {
        return NonVerboseExceptionLog(e, msg)
    }

    override fun forTheUser(e: Throwable, msg: String?) {
        logPrinter.print(NonVerboseExceptionLog(e, msg))
    }

    override fun forTheUser(msg: String) {
        forTheUser(StringLog(msg))
    }

    override fun forTheUser(msg: LogMessage) {
        logPrinter.print(msg)
    }

    override fun newLine() {
        logPrinter.print(NewLineLogMessage)
    }

    override fun forDebugging(msg: String): String { return msg }
    override fun forDebugging(msg: LogMessage) {

    }

    override fun forDebugging(e: Throwable, msg: String?) { }
}

object Verbose : AmountOfDetail {
    override fun exceptionString(e: Throwable, msg: String?): String {
        val message = when(msg) {
            null -> exceptionCauseMessage(e)
            else -> "${msg}: ${e.localizedMessage ?: e.message ?: e.javaClass.name}"
        }

        return "$message${System.lineSeparator()}${e.stackTraceToString()}"
    }

    override fun ofTheException(e: Throwable, msg: String?): LogMessage {
        return VerboseExceptionLog(e, msg)
    }

    override fun forTheUser(e: Throwable, msg: String?) {
        logPrinter.print(VerboseExceptionLog(e, msg))
    }

    override fun forTheUser(msg: String) {
        forTheUser(StringLog(msg))
    }

    override fun forTheUser(msg: LogMessage) {
        logPrinter.print(msg)
    }

    override fun newLine() {
        logPrinter.print(NewLineLogMessage)
    }

    override fun forDebugging(msg: String): String {
        forDebugging(StringLog(msg))
        return msg
    }

    override fun forDebugging(msg: LogMessage) {
        logPrinter.print(msg)
    }

    override fun forDebugging(e: Throwable, msg: String?) {
        forTheUser(e, msg)
    }
}
