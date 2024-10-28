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
            val allOperations = rows.flatMap { it.operations }

            val mergedOperations = mutableListOf<StubUsageReportOperation>()

            for (operation in allOperations) {
                val existingOperation = mergedOperations.find { it.isSameAs(operation) }

                if (existingOperation != null) {
                    mergedOperations[mergedOperations.indexOf(existingOperation)] =
                        existingOperation.copy(count = existingOperation.count + operation.count)
                } else {
                    mergedOperations += operation
                }
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