package application

import io.specmatic.core.pattern.ContractException

class ContractExceptionReport(private val e: ContractException) : CompatibilityReport {
    override fun message(): String {
        return e.report()
    }

    override val exitCode: Int = 1
}