package `in`.specmatic.reports

import `in`.specmatic.conversions.OpenApiSpecification
import `in`.specmatic.conversions.convertPathParameterStyle
import `in`.specmatic.core.log.logger
import `in`.specmatic.stub.isOpenAPI
import `in`.specmatic.stub.isYAML
import java.io.File

class CentralContractRepoReport {
    fun generate(currentWorkingDir:String=""): CentralContractRepoReportJson {
        val searchPath = currentWorkingDir.takeIf { it.isNotEmpty() } ?: File("").canonicalPath
        logger.log("Searching for specification files at: $searchPath")
        val specifications = findSpecifications(searchPath)
        return CentralContractRepoReportJson(getSpecificationRows(specifications, searchPath))
    }

    private fun getSpecificationRows(specifications: List<File>, currentWorkingDir: String): List<SpecificationRow> {
        val currentWorkingDirPath = File(currentWorkingDir).absoluteFile
        return specifications.mapNotNull { spec ->
            val feature = OpenApiSpecification.fromYAML(spec.readText(), spec.path).toFeature()
            if (feature.scenarios.isEmpty()) {
                println("Excluding specification: ${spec.path} as it does not have any paths ")
                null
            } else {
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
    }


    private fun findSpecifications(currentDirectoryPath: String): List<File> {
        val currentDirectory = File(currentDirectoryPath)
        val specifications = mutableListOf<File>()
        val allFiles = currentDirectory.listFiles() ?: emptyArray()
        for (file in allFiles) {
            if (file.isDirectory) {
                specifications.addAll(findSpecifications(file.canonicalPath))
            } else if (isYAML(file.canonicalPath) && isOpenAPI(file.canonicalPath)) {
                specifications.add(file)
            }
        }
        return specifications
    }
}