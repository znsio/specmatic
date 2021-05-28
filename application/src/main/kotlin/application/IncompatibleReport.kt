package application

import `in`.specmatic.core.PATH_NOT_RECOGNIZED_ERROR
import `in`.specmatic.core.Results

data class IncompatibleReport(val results: Results) : CompatibilityReport {
    override fun message(): String {
        return results.report(PATH_NOT_RECOGNIZED_ERROR) + System.lineSeparator() + System.lineSeparator() + "The newer contract is not backward compatible."
    }

    override val exitCode: Int = 1
}