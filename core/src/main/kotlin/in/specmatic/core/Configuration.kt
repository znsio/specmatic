package `in`.specmatic.core

import java.io.File

// moved here because when it's inside, for some reason when SpecmaticJUnitSupport.configFile
// calls globalConfigFileName, it results in a call to the config var and we don't know why,
// and if the configuration file is invalid, an exception is thrown and eaten silently by JUnit's
// test discovery functionality.
// Moving it here so we can use a function to read it which is not inside Configuration
private var innerGlobalConfigFileName: String = "./${Configuration.DEFAULT_CONFIG_FILE_NAME}"
fun getGlobalConfigFileName(): String = innerGlobalConfigFileName

class Configuration {
    companion object {
        var gitCommand: String = System.getProperty("gitCommandPath") ?: System.getenv("SPECMATIC_GIT_COMMAND_PATH") ?: "git"
        const val TEST_BUNDLE_RELATIVE_PATH = ".${APPLICATION_NAME_LOWER_CASE}_test_bundle"
        const val DEFAULT_CONFIG_FILE_NAME = "$APPLICATION_NAME_LOWER_CASE.json"

        var globalConfigFileName: String
            get() = getGlobalConfigFileName()

            set(value) {
                innerGlobalConfigFileName = value
                _config = if(File(innerGlobalConfigFileName).exists())
                    loadSpecmaticJsonConfig(innerGlobalConfigFileName)
                else
                    null

            }

        private var _config: SpecmaticConfigJson? =
            if(File(innerGlobalConfigFileName).exists())
                loadSpecmaticJsonConfig(innerGlobalConfigFileName)
            else
                null

        var config: SpecmaticConfigJson?
            get() {
                return _config
            }

            set(value) {
                _config = value
            }

        private const val ALL_IPV4_ADDRESS_ON_LOCAL_MACHINE = "0.0.0.0"
        const val DEFAULT_HTTP_STUB_HOST = ALL_IPV4_ADDRESS_ON_LOCAL_MACHINE
        const val DEFAULT_HTTP_STUB_PORT = "9000"
    }
}
