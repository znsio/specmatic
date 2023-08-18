package application

import `in`.specmatic.core.Configuration.Companion.globalConfigFileName
import `in`.specmatic.core.DEFAULT_WORKING_DIRECTORY
import `in`.specmatic.core.ReportConfiguration
import `in`.specmatic.core.utilities.ContractPathData
import `in`.specmatic.core.utilities.contractFilePathsFrom
import `in`.specmatic.core.utilities.reportConfigurationFrom
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

    fun reportConfiguration(): ReportConfiguration? {
        return reportConfigurationFrom(globalConfigFileName)
    }
}