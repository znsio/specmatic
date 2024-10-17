package io.specmatic.core

import io.specmatic.core.utilities.Flags
import io.specmatic.core.utilities.Flags.Companion.CONFIG_FILE_PATH
import java.io.File

//TODO: This is a temporary solution need to delete this from Kafka and other dependencies
fun getConfigFileName(): String = getConfigFilePath()
fun getConfigFilePath(): String {
    Flags.getStringValue(CONFIG_FILE_PATH)?.let {
        return it
    }
    val configFileNameWithoutExtension = ".${File.separator}${APPLICATION_NAME_LOWER_CASE}"
    val supportedExtensions = listOf(JSON, YAML, YML)

    val configFileExtension = supportedExtensions.firstOrNull { extension ->
        File("$configFileNameWithoutExtension.$extension").exists()
    } ?: JSON

    return "$configFileNameWithoutExtension.$configFileExtension"
}

class Configuration {
    companion object {
        var gitCommand: String = System.getProperty("gitCommandPath") ?: System.getenv("SPECMATIC_GIT_COMMAND_PATH") ?: "git"
        const val TEST_BUNDLE_RELATIVE_PATH = ".${APPLICATION_NAME_LOWER_CASE}_test_bundle"

        var configFilePath: String
            get() = getConfigFilePath()
            set(value) {
                System.setProperty(CONFIG_FILE_PATH, value)
            }

        private const val ALL_IPV4_ADDRESS_ON_LOCAL_MACHINE = "0.0.0.0"
        const val DEFAULT_HTTP_STUB_HOST = ALL_IPV4_ADDRESS_ON_LOCAL_MACHINE
        const val DEFAULT_HTTP_STUB_PORT = "9000"
        const val DEFAULT_PROXY_PORT = DEFAULT_HTTP_STUB_PORT
        const val DEFAULT_PROXY_HOST = ALL_IPV4_ADDRESS_ON_LOCAL_MACHINE
    }
}
