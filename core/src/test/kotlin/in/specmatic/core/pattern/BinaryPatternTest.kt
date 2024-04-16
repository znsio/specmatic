package `in`.specmatic.core.pattern

import `in`.specmatic.GENERATION
import `in`.specmatic.core.Resolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

class BinaryPatternTest {

    @Test
    @Tag(GENERATION)
    fun `negative patterns should be generated`() {
        val result = BinaryPattern().negativeBasedOn(Row(), Resolver()).map { it.value }.toList()
        assertThat(result.map { it.typeName }).containsExactlyInAnyOrder(
            "null",
            "number",
            "boolean"
        )
    }
}