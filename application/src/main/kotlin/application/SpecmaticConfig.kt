package application

import io.specmatic.core.Configuration.Companion.globalConfigFileName
import io.specmatic.core.DEFAULT_WORKING_DIRECTORY
import io.specmatic.core.utilities.ContractPathData
import io.specmatic.core.utilities.contractFilePathsFrom
import org.springframework.stereotype.Component

@Component
class SpecmaticConfig {
    val configFilePath: String
        get() {
            return globalConfigFileName
        }

    fun contractStubPaths(): List<String> {
        return contractFilePathsFrom(globalConfigFileName, DEFAULT_WORKING_DIRECTORY) { source -> source.stubContracts }.map { it.path }
    }

    fun contractTestPaths(): List<String> {
        return contractFilePathsFrom(globalConfigFileName, DEFAULT_WORKING_DIRECTORY) { source -> source.testContracts }.map { it.path }
    }

    fun contractStubPathData(): List<ContractPathData> {
        return contractFilePathsFrom(globalConfigFileName, DEFAULT_WORKING_DIRECTORY) { source -> source.stubContracts }
    }

    fun contractTestPathData(): List<ContractPathData> {
        return contractFilePathsFrom(globalConfigFileName, DEFAULT_WORKING_DIRECTORY) { source -> source.testContracts }
    }
}