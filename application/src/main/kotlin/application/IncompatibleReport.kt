package application

import `in`.specmatic.core.PATH_NOT_RECOGNIZED_ERROR
import `in`.specmatic.core.Results

data class IncompatibleReport(val results: Results, val message: String = "The newer contract is not backward compatible.") : CompatibilityReport {
    override fun message(): String {
        return results.distinctReport(PATH_NOT_RECOGNIZED_ERROR) + System.lineSeparator() + System.lineSeparator() + message
    }

    override val exitCode: Int = 1
}