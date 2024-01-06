package `in`.specmatic.test.reports.coverage.json

import `in`.specmatic.test.TestResultRecord
import `in`.specmatic.test.reports.coverage.console.Remarks
import kotlinx.serialization.Serializable

@Serializable
class OpenApiCoverageJsonRow {
    private val type: String?
    private val repository: String?
    private val branch: String?
    private val specification: String?
    private val serviceType: String?
    private val operations: List<OpenApiCoverageOperation>

    constructor(
        type: String?,
        repository: String?,
        branch: String?,
        specification: String?,
        serviceType: String?,
        testResults: List<TestResultRecord>
    ) {
        this.type = type;
        this.repository = repository;
        this.branch = branch
        this.specification = specification
        this.serviceType = serviceType
        this.operations = buildOpenApiCoverageOperations(testResults)
    }

    private fun buildOpenApiCoverageOperations(testResults: List<TestResultRecord>): List<OpenApiCoverageOperation> =
        testResults.groupBy {
            Triple(it.path, it.method, it.responseStatus)
        }.map { (operationGroup, operationRows) ->
            OpenApiCoverageOperation(
                path = operationGroup.first,
                method = operationGroup.second,
                responseCode = operationGroup.third,
                count = operationRows.count { it.isExercised },
                coverageStatus = Remarks.resolve(operationRows).toString()
            )
        }
}