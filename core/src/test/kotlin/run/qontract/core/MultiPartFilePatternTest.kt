package run.qontract.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.Result.Failure
import run.qontract.core.Result.Success
import run.qontract.core.pattern.Row

internal class MultiPartFilePatternTest {
    @Test
    fun `should match file parts`() {
        val pattern = MultiPartFilePattern("employeecsv", "@employee.csv", "text/csv", "gzip")
        val value = MultiPartFileValue("employeecsv", "@employee.csv", "text/csv", "gzip")
        assertThat(pattern.matches(value, Resolver())).isInstanceOf(Success::class.java)
    }

    @Test
    fun `should match file parts without content type or encoding`() {
        val pattern = MultiPartFilePattern("employeecsv", "@employee.csv")
        val value = MultiPartFileValue("employeecsv", "@employee.csv")
        assertThat(pattern.matches(value, Resolver())).isInstanceOf(Success::class.java)
    }

    @Test
    fun `should not match file parts with mismatched content type`() {
        val pattern = MultiPartFilePattern("employeecsv", "@employee.csv", "text/plain")
        val value = MultiPartFileValue("employeecsv", "@employee.csv")
        assertThat(pattern.matches(value, Resolver())).isInstanceOf(Failure::class.java)
    }

    @Test
    fun `should not match file parts with mismatched content encoding`() {
        val pattern = MultiPartFilePattern("employeecsv", "@employee.csv", "text/plain")
        val value = MultiPartFileValue("employeecsv", "@employee.csv", "text/plain", "gzip")
        assertThat(pattern.matches(value, Resolver())).isInstanceOf(Failure::class.java)
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

    @Test
    fun `value file name should not have to match the pattern filename`() {
        val pattern = MultiPartFilePattern("employeecsv", "@employee.csv")
        val value = MultiPartFileValue("employeecsv", "@different_filename.csv")
        assertThat(pattern.matches(value, Resolver())).isInstanceOf(Success::class.java)
    }
}