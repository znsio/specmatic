package `in`.specmatic.conversions

class RealEnvironmentConfiguration : EnvironmentConfiguration {
    override fun getEnvironmentVariable(variableName: String): String? {
        return System.getenv(variableName)
    }

    override fun getSystemProperty(variableName: String): String? {
        return System.getProperty(variableName)
    }
}