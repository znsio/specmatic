package `in`.specmatic.core.log

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class NonVerboseTest {
    private val printer = object: LogPrinter {
        var logged: LogMessage = StringLog("")

        override fun print(msg: LogMessage) {
            logged = msg
        }
    }

    private val logger = NonVerbose(CompositePrinter(mutableListOf(printer)))

    @Test
    fun `exception log with msg`() {
        try {
            throw Exception("test")
        } catch(e: Throwable) {
            logger.log(e, "msg")
        }

        val logged = printer.logged
        logged as NonVerboseExceptionLog

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
        logged as NonVerboseExceptionLog

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
    fun `debugging logs do not log anything`() {
        logger.debug("test")
        logger.debug(StringLog("test"))
        try {
            throw Exception("test")
        } catch(e: Throwable) {
            logger.debug(e, "msg")
            logger.debug(e)
        }

        val logged = printer.logged
        assertThat(logged.toLogString()).isEqualTo("")
    }

    @Test
    fun `exception log message without message`() {
        val exceptionLog: NonVerboseExceptionLog = try {
            throw Exception("test")
        } catch(e: Throwable) {
            logger.ofTheException(e)
        } as NonVerboseExceptionLog

        assertThat(exceptionLog.e.message).isEqualTo("test")
        assertThat(exceptionLog.msg).isNull()
    }

    @Test
    fun `exception log message with message`() {
        val exceptionLog: NonVerboseExceptionLog = try {
            throw Exception("test")
        } catch(e: Throwable) {
            logger.ofTheException(e, "msg")
        } as NonVerboseExceptionLog

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

        assertThat(exceptionLogString).isEqualTo("test")
    }

    @Test
    fun `exception log string with message`() {
        val exceptionLogString = try {
            throw Exception("test")
        } catch(e: Throwable) {
            logger.exceptionString(e, "msg")
        }

        assertThat(exceptionLogString).isEqualTo("msg: test")
    }
}
