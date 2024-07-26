package io.specmatic.test.reports.coverage.json

import io.specmatic.test.TestResultRecord
import io.specmatic.test.reports.coverage.CoverageGroupKey
import kotlinx.serialization.Serializable

@Serializable
class OpenApiCoverageJsonReport {
    private val specmaticConfigPath: String
    private val apiCoverage: List<OpenApiCoverageJsonRow>

    constructor(specmaticConfigPath: String, allTests: List<TestResultRecord>) {
        this.specmaticConfigPath = specmaticConfigPath

        val openApiCoverageJsonRows = allTests.groupBy {
            CoverageGroupKey(
                it.sourceProvider,
                it.sourceRepository,
                it.sourceRepositoryBranch,
                it.specification,
                it.serviceType
            )
        }.map { (key, recordsOfGroup) ->
            OpenApiCoverageJsonRow(
                type = key.sourceProvider,
                repository = key.sourceRepository,
                branch = key.sourceRepositoryBranch,
                specification = key.specification,
                serviceType = key.serviceType,
                testResults = recordsOfGroup
            )
        }

        apiCoverage = openApiCoverageJsonRows
    }
}