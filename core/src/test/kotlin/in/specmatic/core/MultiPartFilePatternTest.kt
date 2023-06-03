package `in`.specmatic.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import `in`.specmatic.core.Result.Failure
import `in`.specmatic.core.Result.Success
import `in`.specmatic.core.pattern.ExactValuePattern
import `in`.specmatic.core.pattern.Row
import `in`.specmatic.core.pattern.StringPattern
import `in`.specmatic.core.value.StringValue
import org.junit.jupiter.api.Disabled

internal class MultiPartFilePatternTest {
    private val filenameValue = "employee.csv"
    private val filenameType = ExactValuePattern(StringValue("employee.csv"))

    @Test
    fun `should match file parts`() {
        val pattern = MultiPartFilePattern("employeecsv", filenameType, "text/csv", "gzip")
        val value = MultiPartFileValue("employeecsv", filenameValue, "text/csv", "gzip")
        assertThat(pattern.matches(value, Resolver())).isInstanceOf(Success::class.java)
    }

    @Test
    fun `should match file parts without content type or encoding`() {
        val pattern = MultiPartFilePattern("employeecsv", filenameType)
        val value = MultiPartFileValue("employeecsv", filenameValue)
        assertThat(pattern.matches(value, Resolver())).isInstanceOf(Success::class.java)
    }

    @Disabled
    @Test
    fun `should not match file parts with mismatched content type`() {
        val pattern = MultiPartFilePattern("employeecsv", filenameType, "text/plain")
        val value = MultiPartFileValue("employeecsv", filenameValue)
        assertThat(pattern.matches(value, Resolver())).isInstanceOf(Failure::class.java)
    }

    @Test
    fun `should not match file parts with mismatched content encoding`() {
        val pattern = MultiPartFilePattern("employeecsv", filenameType, "text/plain", "identity")
        val value = MultiPartFileValue("employeecsv", filenameValue, "text/plain", "gzip")
        assertThat(pattern.matches(value, Resolver())).isInstanceOf(Failure::class.java)
    }

    @Test
    fun `ignores content type in value if type is null`() {
        val pattern = MultiPartFilePattern("employeecsv", filenameType)
        val value = MultiPartFileValue("employeecsv", filenameValue, "text/plain", "gzip")
        assertThat(pattern.matches(value, Resolver())).isInstanceOf(Success::class.java)
    }

    @Test
    fun `ignores content encoding in value if type is null`() {
        val pattern = MultiPartFilePattern("employeecsv", filenameType, "text/plain")
        val value = MultiPartFileValue("employeecsv", filenameValue, "text/plain", "gzip")
        assertThat(pattern.matches(value, Resolver())).isInstanceOf(Success::class.java)
    }

    @Test
    fun `it should generate a new pattern`() {
        val pattern = MultiPartFilePattern("employeecsv", filenameType, "text/csv", "gzip")
        val newPattern = pattern.newBasedOn(Row(), Resolver())
        assertThat(newPattern.single()).isEqualTo(pattern)
    }

    @Test
    fun `it should generate a new part`() {
        val pattern = MultiPartFilePattern("employeecsv", filenameType, "text/csv", "gzip")
        val value = MultiPartFileValue("employeecsv", filenameValue, "text/csv", "gzip")
        assertThat(pattern.matches(value, Resolver())).isInstanceOf(Success::class.java)
    }

    @Test
    fun `value file name should match string type`() {
        val pattern = MultiPartFilePattern("employeecsv", StringPattern())
        val value = MultiPartFileValue("employeecsv", "different_filename.csv")
        assertThat(pattern.matches(value, Resolver())).isInstanceOf(Success::class.java)
    }
}