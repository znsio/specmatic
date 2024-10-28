package io.specmatic.stub.report

import kotlinx.serialization.Serializable

@Serializable
data class StubUsageReportJson (
    val specmaticConfigPath:String,
    val stubUsage:List<StubUsageReportRow>
) {
    fun append(other: StubUsageReportJson): StubUsageReportJson {
        val mergedStubUsage = mutableListOf<StubUsageReportRow>()

        val allStubUsageRows = (other.stubUsage + this.stubUsage)

        for (row in allStubUsageRows) {
            val existingRow = mergedStubUsage.find { it.isSameAs(row) }

            if (existingRow == null) {
                mergedStubUsage += row
                continue
            }

            val allOperations = existingRow.operations + row.operations

            val mergedOperations = mutableListOf<StubUsageReportOperation>()

            for (operation in allOperations) {
                val existingOperation = mergedOperations.find { it.isSameAs(operation) }

                if (existingOperation != null) {
                    mergedOperations[mergedOperations.indexOf(existingOperation)] =
                        existingOperation.merge(operation)
                } else {
                    mergedOperations += operation
                }
            }

            mergedStubUsage[mergedStubUsage.indexOf(existingRow)] = existingRow.copy(
                operations = mergedOperations
            )
        }

        return StubUsageReportJson(
            specmaticConfigPath = other.specmaticConfigPath,
            stubUsage = mergedStubUsage
        )
    }
}