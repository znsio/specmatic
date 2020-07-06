package run.qontract.core.value

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.pattern.AnyPattern
import run.qontract.core.pattern.DeferredPattern
import run.qontract.core.pattern.NullPattern
import run.qontract.core.pattern.StringPattern
import run.qontract.optionalPattern
import run.qontract.shouldMatch

internal class StringValueTest {
    @Test
    fun `should generate null matching pattern from pattern with question suffix`() {
        val pattern = DeferredPattern("(string?)").resolvePattern(Resolver())

        val constructedPattern = optionalPattern(StringPattern)

        assertThat(pattern.encompasses(constructedPattern, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)

        StringValue("data") shouldMatch  pattern
        NullValue shouldMatch  pattern
    }
}
