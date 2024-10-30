package io.specmatic.stub.report

import kotlinx.serialization.Serializable

@Serializable
data class StubUsageReportRow(
    val type: String? = null,
    val repository: String? = null,
    val branch: String? = null,
    val specification: String? = null,
    val serviceType: String? = null,
    val operations: List<StubUsageReportOperation>
) {
    fun hasSameRowIdentifiers(other: StubUsageReportRow): Boolean {
        return type.equals(other.type) && repository.equals(other.repository)
                && branch.equals(other.branch) && specification.equals(other.specification)
                && serviceType.equals(other.serviceType)
    }

    fun merge(other: StubUsageReportRow): StubUsageReportRow {
        val allOperations = this.operations + other.operations

        val mergedOperations = mutableListOf<StubUsageReportOperation>()

        for (operation in allOperations) {
            val existingOperation = mergedOperations.find { it.hasSameOperationIdentifiers(operation) }

            if (existingOperation != null) {
                mergedOperations[mergedOperations.indexOf(existingOperation)] =
                    existingOperation.merge(operation)
            } else {
                mergedOperations.add(operation)
            }
        }

        return copy(
            operations = mergedOperations
        )
    }
}
