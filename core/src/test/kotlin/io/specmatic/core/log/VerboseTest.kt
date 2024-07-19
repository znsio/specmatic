package io.specmatic.core.log

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class VerboseTest {
    private val printer = object: LogPrinter {
        val logged: MutableList<LogMessage> = mutableListOf()

        override fun print(msg: LogMessage) {
            logged.add(msg)
        }
    }

    private val logger = Verbose(CompositePrinter(mutableListOf(printer)))

    @Test
    fun `exception log with msg`() {
        try {
            throw Exception("test")
        } catch(e: Throwable) {
            logger.log(e, "msg")
        }

        val logged = printer.logged.first() as VerboseExceptionLog

        assertThat(logged.e.message).isEqualTo("test")
        assertThat(logged.msg).isEqualTo("msg")
    }

    @Test
    fun `exception log without msg`() {
        try {
            throw Exception("test")
        } catch(e: Throwable) {
            logger.log(e)
        }

        val logged = printer.logged.first() as VerboseExceptionLog

        assertThat(logged.e.message).isEqualTo("test")
        assertThat(logged.msg).isNull()
    }

    @Test
    fun `new line log`() {
        logger.newLine()

        assertThat(printer.logged.first()).isEqualTo(NewLineLogMessage)
    }

    @Test
    fun `debugging log string returns string`() {
        assertThat(logger.debug("test")).isEqualTo("test")
    }

    @Test
    fun `debug string log`() {
        val logReturnValue = logger.debug("test")

        val logged = printer.logged.first() as StringLog

        assertThat(logReturnValue).isEqualTo("test")
        assertThat(logged.toLogString()).isEqualTo("test")
    }

    @Test
    fun `debug log message`() {
        logger.debug(StringLog("test"))

        val logged = printer.logged.first() as StringLog

        assertThat(logged).isInstanceOf(StringLog::class.java)
        assertThat(logged.toLogString()).isEqualTo("test")
    }

    @Test
    fun `debug log exception without message`() {
        val logged = try {
            throw Exception("test")
        } catch(e: Throwable) {
            logger.debug(e)
            printer.logged.first() as VerboseExceptionLog
        }

        assertThat(logged).isInstanceOf(VerboseExceptionLog::class.java)
        assertThat(logged.toLogString()).startsWith("test")
        assertThat(logged.toLogString()).contains("java.lang.Exception")
    }

    @Test
    fun `debug log exception with message`() {
        val logged = try {
            throw Exception("test")
        } catch(e: Throwable) {
            logger.debug(e, "msg")
            printer.logged.first() as VerboseExceptionLog
        }

        assertThat(logged).isInstanceOf(VerboseExceptionLog::class.java)
        assertThat(logged.toLogString()).startsWith("msg: test")
        assertThat(logged.toLogString()).contains("java.lang.Exception")
    }

    @Test
    fun `exception log message without message`() {
        val exceptionLog: VerboseExceptionLog = try {
            throw Exception("test")
        } catch(e: Throwable) {
            logger.ofTheException(e)
        } as VerboseExceptionLog

        assertThat(exceptionLog.e.message).isEqualTo("test")
        assertThat(exceptionLog.msg).isNull()
    }

    @Test
    fun `exception log message with message`() {
        val exceptionLog: VerboseExceptionLog = try {
            throw Exception("test")
        } catch(e: Throwable) {
            logger.ofTheException(e, "msg")
        } as VerboseExceptionLog

        assertThat(exceptionLog.e.message).isEqualTo("test")
        assertThat(exceptionLog.msg).isEqualTo("msg")
    }

    @Test
    fun `exception log string without message`() {
        val exceptionLogString = try {
            throw Exception("test")
        } catch(e: Throwable) {
            logger.exceptionString(e)
        }

        assertThat(exceptionLogString).startsWith("test")
        assertThat(exceptionLogString).contains("java.lang.Exception")
    }

    @Test
    fun `exception log string with message`() {
        val exceptionLogString = try {
            throw Exception("test")
        } catch(e: Throwable) {
            logger.exceptionString(e, "msg")
        }

        assertThat(exceptionLogString).startsWith("msg: test")
        assertThat(exceptionLogString).contains("java.lang.Exception")
    }

    @Test
    fun `prints ready message`() {
        logger.keepReady(StringLog("head"))
        logger.print(StringLog("test"))

        assertThat(printer.logged.first().toLogString()).isEqualTo("head")
        assertThat(printer.logged[1].toLogString()).isEqualTo("test")
    }
}
