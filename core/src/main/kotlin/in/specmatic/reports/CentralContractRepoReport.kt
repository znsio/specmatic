package `in`.specmatic.reports

import `in`.specmatic.conversions.OpenApiSpecification
import `in`.specmatic.conversions.convertPathParameterStyle
import `in`.specmatic.core.log.logger
import `in`.specmatic.core.utilities.exceptionCauseMessage
import `in`.specmatic.stub.isOpenAPI
import `in`.specmatic.stub.hasOpenApiFileExtension
import java.io.File

class CentralContractRepoReport {
    fun generate(currentWorkingDir: String = ""): CentralContractRepoReportJson {
        val searchPath = currentWorkingDir.takeIf { it.isNotEmpty() } ?: File("").canonicalPath
        logger.log("Searching for specification files at: $searchPath")
        val specifications = findSpecifications(searchPath)
        return CentralContractRepoReportJson(getSpecificationRows(specifications.sorted(), searchPath))
    }

    private fun getSpecificationRows(specifications: List<File>, currentWorkingDir: String): List<SpecificationRow> {
        val currentWorkingDirPath = File(currentWorkingDir).absoluteFile

        return specifications.mapNotNull {
            try {
                val feature = OpenApiSpecification.fromYAML(it.readText(), it.path).toFeature()
                Pair(it, feature)
            }
            catch (e:Throwable){
                logger.log("Could not parse ${it.path} due to the following error:")
                logger.log(exceptionCauseMessage(e))
                null
            }
        }
       .filter { (spec, feature) ->
            if (feature.scenarios.isEmpty()) {
                logger.log("Excluding specification: ${spec.path} as it does not have any paths ")
            }
            feature.scenarios.isNotEmpty()
        }.map { (spec, feature) ->
            SpecificationRow(
                spec.relativeTo(currentWorkingDirPath).path,
                feature.serviceType,
                feature.scenarios.map {
                    SpecificationOperation(
                        convertPathParameterStyle(it.path),
                        it.method,
                        it.httpResponsePattern.status
                    )
                }
            )
        }
    }


    private fun findSpecifications(currentDirectoryPath: String): List<File> {
        val currentDirectory = File(currentDirectoryPath)
        val specifications = mutableListOf<File>()
        val allFiles = currentDirectory.listFiles() ?: emptyArray()
        for (file in allFiles) {
            if (file.isDirectory) {
                specifications.addAll(findSpecifications(file.canonicalPath))
            } else if (hasOpenApiFileExtension(file.canonicalPath) && isOpenAPI(file.canonicalPath)) {
                specifications.add(file)
            }
        }
        return specifications
    }
}