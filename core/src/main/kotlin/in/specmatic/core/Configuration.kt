package `in`.specmatic.core

import java.io.File

class Configuration {
    companion object {
        const val TEST_BUNDLE_RELATIVE_PATH = ".${APPLICATION_NAME_LOWER_CASE}_test_bundle"
        const val DEFAULT_CONFIG_FILE_NAME = "$APPLICATION_NAME_LOWER_CASE.json"

        private var _globalConfigFileName: String = "./$DEFAULT_CONFIG_FILE_NAME"
        var globalConfigFileName: String
            get() = _globalConfigFileName

            set(value) {
                _globalConfigFileName = value
                _config = loadSpecmaticJsonConfig(_globalConfigFileName)
            }

        private var _config: SpecmaticConfigJson? =
            if(File(_globalConfigFileName).exists())
                loadSpecmaticJsonConfig(_globalConfigFileName)
            else
                null

        val config: SpecmaticConfigJson?
            get() {
                return _config
            }

        private const val ALL_IPV4_ADDRESS_ON_LOCAL_MACHINE = "0.0.0.0"
        const val DEFAULT_HTTP_STUB_HOST = ALL_IPV4_ADDRESS_ON_LOCAL_MACHINE
        const val DEFAULT_HTTP_STUB_PORT = "9000"
    }
}
