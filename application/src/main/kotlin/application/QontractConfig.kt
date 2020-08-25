package application

import org.springframework.stereotype.Component
import run.qontract.core.utilities.contractFilePathsFrom

@Component
class QontractConfig {
    fun contractStubPaths(): List<String> {
        return contractFilePathsFrom(QONTRACT_CONFIG_IN_CURRENT_DIRECTORY, WORKING_DIRECTORY) { source -> source.stubContracts }
    }

    companion object {
        const val QONTRACT_CONFIG_FILE_NAME = "qontract.json"
        const val QONTRACT_CONFIG_IN_CURRENT_DIRECTORY = "./$QONTRACT_CONFIG_FILE_NAME"
        const val WORKING_DIRECTORY = ".qontract"
    }
}