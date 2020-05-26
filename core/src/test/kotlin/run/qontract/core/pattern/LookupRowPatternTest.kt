package run.qontract.core.pattern

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.resultReport
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

    @Test
    fun `should encompass itself`() {
        val lookupRowPattern = LookupRowPattern(StringPattern, "name")
        val result = lookupRowPattern.encompasses(lookupRowPattern, Resolver(), Resolver())

        println(resultReport(result))
        assertThat(result).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should resolve deferreds that it has to lookup when testing encompassment`() {
        val lookupRowPattern = LookupRowPattern(DeferredPattern("(string)"), "name")
        assertThat(lookupRowPattern.encompasses(StringPattern, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should encompass enclosed pattern`() {
        val lookupRowPattern = LookupRowPattern(StringPattern, "name")
        assertThat(lookupRowPattern.encompasses(StringPattern, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }
}
