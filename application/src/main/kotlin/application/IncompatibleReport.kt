package application

import `in`.specmatic.core.Results

data class IncompatibleReport(val results: Results) : CompatibilityReport {
    override fun message(): String {
        return results.report() + System.lineSeparator() + System.lineSeparator() + "The newer contract is not backward compatible."
    }
}