package run.qontract.core.pattern

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.shouldNotMatch
import run.qontract.core.value.NullValue

internal class DeferredPatternTest {
    @Test
    fun `should fail to match nulls gracefully`() {
        NullValue shouldNotMatch DeferredPattern("(string)", null)
    }

    @Test
    fun `should encompass itself`() {
        val deferredPattern = DeferredPattern("(string)")
        assertThat(deferredPattern.encompasses(deferredPattern, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }
}
