package io.specmatic.stub.report

import kotlinx.serialization.Serializable

@Serializable
data class StubUsageReportJson (
    val specmaticConfigPath:String,
    val stubUsage:List<StubUsageReportRow>
) {
    fun append(anotherStubUsageReport: StubUsageReportJson): StubUsageReportJson {
        val mergedStubUsage = mutableListOf<StubUsageReportRow>()

        val allStubUsageRows = (anotherStubUsageReport.stubUsage + this.stubUsage).groupBy {
            Triple(it.type, it.repository, it.specification)
        }

        for ((_, rows) in allStubUsageRows) {
            val mergedOperations = mutableListOf<StubUsageReportOperation>()

            rows.flatMap { it.operations }.groupBy { op ->
                Triple(op.path, op.method, op.responseCode)
            }.forEach { (_, ops) ->
                val combinedCount = ops.sumOf { it.count }
                val sampleOp = ops.first()
                mergedOperations += sampleOp.copy(count = combinedCount)
            }

            val firstRow = rows.first()
            mergedStubUsage += StubUsageReportRow(
                type = firstRow.type,
                repository = firstRow.repository,
                branch = firstRow.branch,
                specification = firstRow.specification,
                serviceType = firstRow.serviceType,
                operations = mergedOperations
            )
        }

        return StubUsageReportJson(
            specmaticConfigPath = anotherStubUsageReport.specmaticConfigPath,
            stubUsage = mergedStubUsage
        )
    }
}