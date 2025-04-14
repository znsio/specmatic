package application

import io.specmatic.core.Configuration
import io.specmatic.core.DEFAULT_WORKING_DIRECTORY
import io.specmatic.core.utilities.ContractPathData
import io.specmatic.core.utilities.contractFilePathsFrom

class SpecmaticConfig {
    fun contractStubPaths(): List<String> {
        return contractFilePathsFrom(Configuration.configFilePath, DEFAULT_WORKING_DIRECTORY) { source -> source.stubContracts }.map { it.path }
    }

    fun contractTestPaths(): List<String> {
        return contractFilePathsFrom(Configuration.configFilePath, DEFAULT_WORKING_DIRECTORY) { source -> source.testContracts }.map { it.path }
    }

    fun contractStubPathData(): List<ContractPathData> {
        return contractFilePathsFrom(Configuration.configFilePath, DEFAULT_WORKING_DIRECTORY) { source -> source.stubContracts }
    }
}