package application

object CompatibleReport: CompatibilityReport {
    override fun message(): String {
        return "The newer contract is backward compatible."
    }

    override val exitCode: Int = 0
}