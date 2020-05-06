package run.qontract.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import run.qontract.core.Result.Success
import run.qontract.core.pattern.Row
import run.qontract.core.pattern.StringPattern
import run.qontract.core.value.StringValue

internal class MultiPartFilePatternTest {
    @Test
    fun `should match file parts`() {
        val pattern = MultiPartFilePattern("employeecsv", "@employee.csv", "text/csv", "gzip")
        val value = MultiPartFileValue("employeecsv", "@employee.csv", "text/csv", "gzip")
        assertThat(pattern.matches(value, Resolver())).isInstanceOf(Success::class.java)
    }

    @Test
    fun `it should generate a new pattern`() {
        val pattern = MultiPartFilePattern("employeecsv", "@employee.csv", "text/csv", "gzip")
        val newPattern = pattern.newBasedOn(Row(), Resolver())
        assertThat(newPattern.single()).isEqualTo(pattern)
    }

    @Test
    fun `it should generate a new part`() {
        val pattern = MultiPartFilePattern("employeecsv", "@employee.csv", "text/csv", "gzip")
        val value = MultiPartFileValue("employeecsv", "@employee.csv", "text/csv", "gzip")
        assertThat(pattern.matches(value, Resolver())).isInstanceOf(Success::class.java)
    }
}