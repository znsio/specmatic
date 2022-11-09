package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result.Failure
import `in`.specmatic.core.Result.Success
import `in`.specmatic.core.value.StringValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.function.Consumer

internal class CsvPatternTest {
    @Test
    fun `it should match a CSV containing numeric values`() {
        assertThat(CsvPattern(NumberPattern()).matches(StringValue("1,2,3"), Resolver())).isInstanceOf(Success::class.java)
    }

    @Test
    fun `it should match a CSV with only one value`() {
        assertThat(CsvPattern(NumberPattern()).matches(StringValue("1"), Resolver())).isInstanceOf(Success::class.java)
    }

    @Test
    fun `it should return an error when a value in the CSV is of the wrong type`() {
        assertThat(CsvPattern(NumberPattern()).matches(StringValue("1,b"), Resolver())).isInstanceOf(Failure::class.java)
    }

    @Test
    fun `it should generate a CSV containing multiple values`() {
        assertThat(CsvPattern(NumberPattern()).generate(Resolver()).toStringLiteral()).contains(",")

    }

    @Test
    fun `each value in the generated CSV must be of the required type`() {
        val csv = CsvPattern(NumberPattern()).generate(Resolver()).toStringLiteral()
        val parts = csv.split(",")

        assertThat(parts).allSatisfy {
            assertDoesNotThrow { NumberPattern().parse(it, Resolver()) }
        }
    }

    @Test
    fun `generates values for negative tests`() {
        val negativeTypes = CsvPattern(NumberPattern()).negativeBasedOn(Row(), Resolver())
        assertThat(negativeTypes).hasSize(3)
    }

    @Test
    fun `generates values for tests`() {
        assertThat(CsvPattern(NumberPattern()).newBasedOn(Row(), Resolver())).satisfies(Consumer {
            assertThat(it).hasSize(1)
            assertThat(it.first()).isInstanceOf(CsvPattern::class.java)
            assertThat(it.first().pattern).isInstanceOf(NumberPattern::class.java)
        })
    }

    @Test
    fun `generates values for backward compatibility check`() {
        assertThat(CsvPattern(NumberPattern()).newBasedOn(Resolver())).satisfies(Consumer {
            assertThat(it).hasSize(1)
            assertThat(it.first()).isInstanceOf(CsvPattern::class.java)
            assertThat(it.first().pattern).isInstanceOf(NumberPattern::class.java)
        })
    }
}