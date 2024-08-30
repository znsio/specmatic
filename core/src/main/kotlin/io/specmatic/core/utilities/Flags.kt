package io.specmatic.core.utilities

class Flags {
    companion object {
        const val SPECMATIC_GENERATIVE_TESTS = "SPECMATIC_GENERATIVE_TESTS"
        const val ONLY_POSITIVE = "ONLY_POSITIVE"
        const val VALIDATE_RESPONSE_VALUE = "VALIDATE_RESPONSE_VALUE"
        const val EXTENSIBLE_SCHEMA = "EXTENSIBLE_SCHEMA"
        const val MAX_TEST_REQUEST_COMBINATIONS = "MAX_TEST_REQUEST_COMBINATIONS"
        const val SCHEMA_EXAMPLE_DEFAULT = "SCHEMA_EXAMPLE_DEFAULT"
        const val SPECMATIC_TEST_PARALLELISM = "SPECMATIC_TEST_PARALLELISM"
        const val SPECMATIC_STUB_DELAY = "SPECMATIC_STUB_DELAY"
        const val SPECMATIC_TEST_TIMEOUT = "SPECMATIC_TEST_TIMEOUT"

        const val SPECMATIC_PRETTY_PRINT = "SPECMATIC_PRETTY_PRINT"
        const val EXAMPLE_DIRECTORIES = "EXAMPLE_DIRECTORIES"

        fun getStringValue(flagName: String): String? = System.getenv(flagName) ?: System.getProperty(flagName)

        fun getBooleanValue(flagName: String, default: Boolean = false) = getStringValue(flagName)?.toBoolean() ?: default

        fun getLongValue(flagName: String, default: Long? = null): Long? = ( getStringValue(flagName))?.toLong() ?: default
    }
}