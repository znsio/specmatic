package run.qontract.core.value

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.Resolver
import run.qontract.core.pattern.AnyPattern
import run.qontract.core.pattern.DeferredPattern
import run.qontract.core.pattern.NullPattern
import run.qontract.core.pattern.StringPattern
import run.qontract.core.shouldMatch

internal class StringValueTest {
    @Test
    fun `should generate null matching pattern from pattern with question suffix`() {
        val pattern = DeferredPattern("(string?)").resolvePattern(Resolver())

        val constructedPattern = AnyPattern(listOf(NullPattern, StringPattern))

        println(pattern)
        println(constructedPattern)

        assertThat(pattern.matchesPattern(constructedPattern, Resolver())).isTrue()

        StringValue("data") shouldMatch  pattern
        NullValue shouldMatch  pattern
    }
}
