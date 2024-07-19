package application

import io.specmatic.core.utilities.exceptionCauseMessage

class ExceptionReport(private val e: Throwable) : CompatibilityReport {
    override fun message(): String {
        return "Error: " + exceptionCauseMessage(e)
    }

    override val exitCode: Int = 1
}