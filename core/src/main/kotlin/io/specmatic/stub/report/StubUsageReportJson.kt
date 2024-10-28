package io.specmatic.stub.report

import kotlinx.serialization.Serializable

@Serializable
data class StubUsageReportJson (
    val specmaticConfigPath:String,
    val stubUsage:List<StubUsageReportRow>
) {
    fun merge(other: StubUsageReportJson): StubUsageReportJson {
        val mergedStubUsageRows: MutableList<StubUsageReportRow> = mutableListOf<StubUsageReportRow>()

        val allStubUsageRows = (other.stubUsage + this.stubUsage)

        for (stubUsageReportRow in allStubUsageRows) {
            val existingRow = mergedStubUsageRows.find { it.hasSameRowIdentifiers(stubUsageReportRow) }

            if (existingRow == null) {
                mergedStubUsageRows.add(stubUsageReportRow)
                continue
            }

            mergedStubUsageRows[mergedStubUsageRows.indexOf(existingRow)] = existingRow.merge(stubUsageReportRow)
        }

        return StubUsageReportJson(
            specmaticConfigPath = other.specmaticConfigPath,
            stubUsage = mergedStubUsageRows
        )
    }
}