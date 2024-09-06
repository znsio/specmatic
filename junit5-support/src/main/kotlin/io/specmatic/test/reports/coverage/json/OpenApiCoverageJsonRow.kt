package io.specmatic.test.reports.coverage.json

import io.specmatic.test.OpenApiTestResultRecord
import io.specmatic.test.report.Remarks
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
        testResults: List<OpenApiTestResultRecord>
    ) {
        this.type = type
        this.repository = repository
        this.branch = branch
        this.specification = specification
        this.serviceType = serviceType
        this.operations = buildOpenApiCoverageOperations(testResults)
    }

    private fun buildOpenApiCoverageOperations(testResults: List<OpenApiTestResultRecord>): List<OpenApiCoverageOperation> =
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