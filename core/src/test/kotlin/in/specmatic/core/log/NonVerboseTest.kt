package `in`.specmatic.core.log

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class NonVerboseTest {
    private val printer = object: LogPrinter {
        val logged: MutableList<LogMessage> = mutableListOf()

        override fun print(msg: LogMessage) {
            logged.add(msg)
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

        val logged = printer.logged.first() as NonVerboseExceptionLog

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

        val logged = printer.logged.first() as NonVerboseExceptionLog

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
    fun `debugging logs do not log anything`() {
        logger.debug("test")
        logger.debug(StringLog("test"))

        try {
            throw Exception("test")
        } catch(e: Throwable) {
            logger.debug(e, "msg")
            logger.debug(e)
        }

        assertThat(printer.logged).isEmpty()
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

    @Test
    fun `prints ready message`() {
        logger.keepReady(StringLog("head"))
        logger.print(StringLog("test"))

        assertThat(printer.logged.first().toLogString()).isEqualTo("head")
        assertThat(printer.logged.get(1).toLogString()).isEqualTo("test")
    }
}
