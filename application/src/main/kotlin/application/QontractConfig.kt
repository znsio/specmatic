package application

import org.springframework.stereotype.Component
import `in`.specmatic.core.Constants.Companion.DEFAULT_QONTRACT_CONFIG_IN_CURRENT_DIRECTORY
import `in`.specmatic.core.CONTRACT_EXTENSION
import `in`.specmatic.core.utilities.ContractPathData
import `in`.specmatic.core.utilities.contractFilePathsFrom

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
        const val WORKING_DIRECTORY = ".$CONTRACT_EXTENSION"
    }
}