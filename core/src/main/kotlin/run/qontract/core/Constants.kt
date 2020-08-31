package run.qontract.core

class Constants {
    companion object {
        const val QONTRACT_CONFIG_FILE_NAME = "qontract.json"
        const val QONTRACT_CONFIG_IN_CURRENT_DIRECTORY = "./$QONTRACT_CONFIG_FILE_NAME"
        private const val ALL_IPV4_ADDRESS_ON_LOCAL_MACHINE = "0.0.0.0"
        const val DEFAULT_HTTP_STUB_HOST = ALL_IPV4_ADDRESS_ON_LOCAL_MACHINE
        const val DEFAULT_HTTP_STUB_PORT = "9000"
    }
}