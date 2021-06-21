package `in`.specmatic.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import `in`.specmatic.core.Result.Success
import `in`.specmatic.core.pattern.ExactValuePattern
import `in`.specmatic.core.pattern.NumberPattern
import `in`.specmatic.core.pattern.Row
import `in`.specmatic.core.pattern.StringPattern
import `in`.specmatic.core.value.NumberValue
import `in`.specmatic.core.value.StringValue

internal class MultiPartContentPatternTest {
    @Test
    fun `match a multi part non-file part` () {
        val value = MultiPartContentValue("employeeid", StringValue("10"))
        val pattern = MultiPartContentPattern("employeeid", StringPattern())

        assertThat(pattern.matches(value, Resolver())).isInstanceOf(Success::class.java)
    }

    @Test
    fun `it should generate a new pattern`() {
        val pattern = MultiPartContentPattern("employeeid", StringPattern())
        val newPattern = pattern.newBasedOn(Row(), Resolver())
        assertThat(newPattern.single()).isEqualTo(pattern)
    }

    @Test
    fun `it should generate a new part`() {
        val pattern = MultiPartContentPattern("employeeid", StringPattern())
        val value = MultiPartContentValue("employeeid", StringValue("data"))
        assertThat(pattern.matches(value, Resolver())).isInstanceOf(Success::class.java)
    }

    @Test
    fun `should generate a new pattern based on row values when the row has a column with the part name`() {
        val pattern = MultiPartContentPattern("id", NumberPattern)
        val row = Row(listOf("id"), listOf("10"))
        val patterns = pattern.newBasedOn(row, Resolver()).map { it as MultiPartContentPattern }

        assertThat(patterns).hasSize(1)

        assertThat(patterns.single().name).isEqualTo("id")
        assertThat(patterns.single().content).isEqualTo(ExactValuePattern(NumberValue(10)))
    }

    @Test
    fun `content pattern should match value with pattern content in mock mode`() {
        val pattern = MultiPartContentPattern("id", NumberPattern)
        val value = MultiPartContentValue("id", StringValue("(number)"))

        val mockModeResolver = Resolver(mockMode = true)

        assertThat(pattern.matches(value, mockModeResolver)).isInstanceOf(Success::class.java)
    }
}