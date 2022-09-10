package application

class CompatibleReport(val msg: String = "The newer contract is backward compatible."): CompatibilityReport {
    override fun message(): String {
        return msg
    }

    override val exitCode: Int = 0
}