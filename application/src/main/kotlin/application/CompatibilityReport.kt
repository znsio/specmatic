package application

interface CompatibilityReport {
    fun message(): String
    val exitCode: Int
}