package application

import org.springframework.stereotype.Component
import `in`.specmatic.core.Configuration.Companion.globalConfigFileName
import `in`.specmatic.core.CONTRACT_EXTENSION
import `in`.specmatic.core.utilities.ContractPathData
import `in`.specmatic.core.utilities.contractFilePathsFrom

@Component
class QontractConfig {
    fun contractStubPaths(): List<String> {
        return contractFilePathsFrom(globalConfigFileName, WORKING_DIRECTORY) { source -> source.stubContracts }.map { it.path }
    }

    fun contractTestPaths(): List<String> {
        return contractFilePathsFrom(globalConfigFileName, WORKING_DIRECTORY) { source -> source.testContracts }.map { it.path }
    }

    fun contractStubPathData(): List<ContractPathData> {
        return contractFilePathsFrom(globalConfigFileName, WORKING_DIRECTORY) { source -> source.stubContracts }
    }

    companion object {
        const val WORKING_DIRECTORY = ".$CONTRACT_EXTENSION"
    }
}