package io.specmatic.stub.report

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StubUsageReportRowTest {
    private val operation1 = StubUsageReportOperation(path = "/api/test1", method = "GET", responseCode = 200, count = 5)
    private val operation2 = StubUsageReportOperation(path = "/api/test2", method = "POST", responseCode = 201, count = 10)
    private val operation3 = StubUsageReportOperation(path = "/api/test1", method = "GET", responseCode = 200, count = 2)

    @Test
    fun `hasSameRowIdentifiers should return true for matching identifiers`() {
        val row1 = StubUsageReportRow(type = "type1", repository = "repo1", branch = "main", specification = "spec1", serviceType = "serviceA", operations = listOf(operation1))
        val row2 = StubUsageReportRow(type = "type1", repository = "repo1", branch = "main", specification = "spec1", serviceType = "serviceA", operations = listOf(operation2))

        val result = row1.hasSameRowIdentifiers(row2)

        assertTrue(result)
    }

    @Test
    fun `hasSameRowIdentifiers should return false for non-matching identifiers`() {
        val row1 = StubUsageReportRow(type = "type1", repository = "repo1", branch = "main", specification = "spec1", serviceType = "serviceA", operations = listOf(operation1))
        val row2 = StubUsageReportRow(type = "type2", repository = "repo1", branch = "main", specification = "spec1", serviceType = "serviceA", operations = listOf(operation2))

        val result = row1.hasSameRowIdentifiers(row2)

        assertFalse(result)
    }

    @Test
    fun `merge should combine operations of two rows with unique operations`() {
        val row1 = StubUsageReportRow(type = "type1", repository = "repo1", branch = "main", specification = "spec1", serviceType = "serviceA", operations = listOf(operation1))
        val row2 = StubUsageReportRow(type = "type1", repository = "repo1", branch = "main", specification = "spec1", serviceType = "serviceA", operations = listOf(operation2))

        val mergedRow = row1.merge(row2)

        assertThat(mergedRow.operations).hasSize(2)
        assertThat(mergedRow.operations).containsExactlyInAnyOrder(operation1, operation2)
    }

    @Test
    fun `merge should combine operations of two rows with overlapping operations`() {
        val row1 = StubUsageReportRow(type = "type1", repository = "repo1", branch = "main", specification = "spec1", serviceType = "serviceA", operations = listOf(operation1))
        val row2 = StubUsageReportRow(type = "type1", repository = "repo1", branch = "main", specification = "spec1", serviceType = "serviceA", operations = listOf(operation3))

        val mergedRow = row1.merge(row2)

        assertThat(mergedRow.operations).hasSize(1)
        assertThat(mergedRow.operations[0].count).isEqualTo(7)
    }
}