package run.qontract.core.pattern

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import run.qontract.core.Resolver
import run.qontract.core.value.NumberValue

internal class LookupRowPatternTest {
    @Test
    fun `it should return a new exact value pattern when generating pattern from row with a matching key`() {
        val pattern = LookupRowPattern(NumberTypePattern, "customerId")
        val row = Row(listOf("customerId"), listOf("10"))

        val newPattern = pattern.newBasedOn(row, Resolver())
        assertThat(newPattern.single()).isEqualTo(ExactValuePattern(NumberValue(10)))
    }

    @Test
    fun `it should return a new exact value pattern when generating pattern from row with no matching key`() {
        val pattern = LookupRowPattern(NumberTypePattern, "customerId")
        val row = Row(emptyList(), emptyList())

        val newPattern = pattern.newBasedOn(row, Resolver())
        assertThat(newPattern.single()).isEqualTo(NumberTypePattern)
    }
}
