package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.value.StringValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StringlyNegativePatternsTest {
    @Test
    fun `there should be no negative patterns for strings`() {
        val patternMap = mapOf("key" to StringPattern())
        val resolver = Resolver()
        val row = Row()

        val negativePatterns: List<Map<String, Pattern>> = StringlyNegativePatterns().negativeBasedOn(patternMap, row, resolver)

        assertThat(negativePatterns).isEmpty()

    }

    @Test
    fun `negative patterns for non-strings should not include NullPattern`() {
        val patternMap = mapOf("key" to NumberPattern())
        val resolver = Resolver()
        val row = Row()

        val negativePatterns: List<Map<String, Pattern>> = StringlyNegativePatterns().negativeBasedOn(patternMap, row, resolver)

        assertThat(negativePatterns).containsExactlyInAnyOrder(
            mapOf("key" to BooleanPattern()),
            mapOf("key" to StringPattern()),
        )
    }

    @Test
    fun `negative patterns for enum should have the negatives of the pattern of the enum value excluding NullPattern`() {
        val patternMap = mapOf("key" to EnumPattern(listOf(StringValue("one"))))
        val resolver = Resolver()
        val row = Row()

        val negativePatterns: List<Map<String, Pattern>> = StringlyNegativePatterns().negativeBasedOn(patternMap, row, resolver)
        assertThat(negativePatterns).containsExactlyInAnyOrder(
            mapOf("key" to NumberPattern()),
            mapOf("key" to BooleanPattern())
        )

    }
}