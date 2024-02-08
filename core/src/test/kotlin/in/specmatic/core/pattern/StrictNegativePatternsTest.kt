package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StrictNegativePatternsTest {
    @Test
    fun `should return negative patterns for each key`() {
        val patternMap = mapOf("key" to StringPattern())
        val resolver = Resolver()
        val row = Row()

        val negativePatterns: List<Map<String, Pattern>> = StrictNegativePatterns().negativeBasedOn(patternMap, row, resolver)

        assertThat(negativePatterns).containsExactlyInAnyOrder(
            mapOf("key" to NumberPattern()),
            mapOf("key" to BooleanPattern()),
            mapOf("key" to NullPattern)
        )
    }
}