package `in`.specmatic.core.pattern

import `in`.specmatic.GENERATION
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.value.NullValue
import `in`.specmatic.shouldNotMatch
import org.junit.jupiter.api.Tag

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


    @Test
    @Tag(GENERATION)
    fun `negative patterns should be generated`() {
        val result = DeferredPattern("(string)").negativeBasedOn(Row(), Resolver()).toList()
        assertThat(result.map { it.typeName }).containsExactlyInAnyOrder(
            "null",
            "number",
            "boolean"
        )
    }
}
