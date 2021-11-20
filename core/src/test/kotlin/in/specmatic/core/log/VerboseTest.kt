package `in`.specmatic.core.log

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class VerboseTest {
    private val printer = object: LogPrinter {
        var logged: LogMessage = StringLog("")

        override fun print(msg: LogMessage) {
            logged = msg
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

        val logged = printer.logged
        logged as VerboseExceptionLog

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

        val logged = printer.logged
        logged as VerboseExceptionLog

        assertThat(logged.e.message).isEqualTo("test")
        assertThat(logged.msg).isNull()
    }

    @Test
    fun `new line log`() {
        logger.newLine()

        val logged = printer.logged
        assertThat(logged).isInstanceOf(NewLineLogMessage::class.javaObjectType)
    }

    @Test
    fun `debugging log string returns string`() {
        assertThat(logger.debug("test")).isEqualTo("test")
    }

    @Test
    fun `debug string log`() {
        val logReturnValue = logger.debug("test")

        val logged = printer.logged
        assertThat(logReturnValue).isEqualTo("test")
        assertThat(logged.toLogString()).isEqualTo("test")
    }

    @Test
    fun `debug log message`() {
        logger.debug(StringLog("test"))

        val logged = printer.logged

        assertThat(logged).isInstanceOf(StringLog::class.java)
        assertThat(logged.toLogString()).isEqualTo("test")
    }

    @Test
    fun `debug log exception without message`() {
        val logged = try {
            throw Exception("test")
        } catch(e: Throwable) {
            logger.debug(e)
            printer.logged
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
            printer.logged
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
}
