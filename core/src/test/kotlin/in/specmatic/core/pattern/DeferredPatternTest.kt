package `in`.specmatic.core.pattern

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.value.NullValue
import `in`.specmatic.shouldNotMatch

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
