package application

import org.springframework.stereotype.Component
import run.qontract.core.Constants.Companion.DEFAULT_QONTRACT_CONFIG_IN_CURRENT_DIRECTORY
import run.qontract.core.utilities.ContractPathData
import run.qontract.core.utilities.contractFilePathsFrom

@Component
class QontractConfig {
    fun contractStubPaths(): List<String> {
        return contractFilePathsFrom(DEFAULT_QONTRACT_CONFIG_IN_CURRENT_DIRECTORY, WORKING_DIRECTORY) { source -> source.stubContracts }.map { it.path }
    }

    fun contractTestPaths(): List<String> {
        return contractFilePathsFrom(DEFAULT_QONTRACT_CONFIG_IN_CURRENT_DIRECTORY, WORKING_DIRECTORY) { source -> source.testContracts }.map { it.path }
    }

    fun contractStubPathData(): List<ContractPathData> {
        return contractFilePathsFrom(DEFAULT_QONTRACT_CONFIG_IN_CURRENT_DIRECTORY, WORKING_DIRECTORY) { source -> source.stubContracts }
    }

    companion object {
        const val WORKING_DIRECTORY = ".qontract"
    }
}