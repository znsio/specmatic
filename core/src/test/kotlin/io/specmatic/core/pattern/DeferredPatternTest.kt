package io.specmatic.core.pattern

import io.specmatic.GENERATION
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import io.specmatic.core.Resolver
import io.specmatic.core.Result
import io.specmatic.core.value.NullValue
import io.specmatic.shouldNotMatch
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
        val result = DeferredPattern("(string)").negativeBasedOn(Row(), Resolver()).map { it.value }.toList()
        assertThat(result.map { it.typeName }).containsExactlyInAnyOrder(
            "null",
            "number",
            "boolean"
        )
    }
}
