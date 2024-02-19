package `in`.specmatic.conversions

class DefaultEnvironment : Environment {
    override fun getEnvironmentVariable(variableName: String): String? {
        return System.getenv(variableName)
    }
}