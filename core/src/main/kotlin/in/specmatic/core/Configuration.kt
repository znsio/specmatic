package `in`.specmatic.core

class Configuration {
    companion object {
        const val TEST_BUNDLE_RELATIVE_PATH = ".${APPLICATION_NAME_LOWER_CASE}_test_bundle"
        const val DEFAULT_CONFIG_FILE_NAME = "$APPLICATION_NAME_LOWER_CASE.json"

        var globalConfigFileName: String = "./$DEFAULT_CONFIG_FILE_NAME"

        private const val ALL_IPV4_ADDRESS_ON_LOCAL_MACHINE = "0.0.0.0"
        const val DEFAULT_HTTP_STUB_HOST = ALL_IPV4_ADDRESS_ON_LOCAL_MACHINE
        const val DEFAULT_HTTP_STUB_PORT = "9000"
    }
}