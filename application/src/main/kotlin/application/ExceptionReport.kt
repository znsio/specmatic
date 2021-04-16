package application

import `in`.specmatic.core.utilities.exceptionCauseMessage

class ExceptionReport(private val e: Throwable) : CompatibilityReport {
    override fun message(): String {
        return "Exception thrown: " + exceptionCauseMessage(e)
    }
}