package `in`.specmatic.core.pattern

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import `in`.specmatic.core.Resolver
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

internal class JSONArrayPatternKtTest {
    @Test
    fun `when there is one nullable in a list two array types should be generated`() {
        val arrayType = parsedPattern("""["(number?)", "(number)"]""")

        val patterns = arrayType.newBasedOn(Row(), Resolver()).map { it as JSONArrayPattern }
        assertThat(patterns).hasSize(2)
        assertThat(patterns).contains(JSONArrayPattern(listOf<Pattern>(NullPattern, NumberPattern)))
        assertThat(patterns).contains(JSONArrayPattern(listOf<Pattern>(NullPattern, NumberPattern)))

        println(patterns.size)
        for(json in patterns) println(json)
    }

    @Nested
    @DisplayName("Given [optional, required]")
    inner class FirstCompulsorySecondRequired {
        private val combinations = allOrNothingListCombinations(listOf(listOf(StringPattern, null), listOf(NumberPattern)))

        @Test
        fun `two results should be generated`() {
            assertThat(combinations).hasSize(2)
        }

        @Test
        fun `one result should have only the required type`() {
            assertThat(combinations).contains(listOf(NumberPattern))
        }

        @Test
        fun `the other result should have both types with order preserved`() {
            assertThat(combinations).contains(listOf(StringPattern, NumberPattern))
        }
    }

    @Nested
    @DisplayName("Given [optional, required, optional, required]")
    inner class OneCompulsoryAndOneRequired {
        private val combinations = allOrNothingListCombinations(listOf(listOf(StringPattern, null), listOf(NumberPattern), listOf(BooleanPattern, null), listOf(DateTimePattern)))

        @Test
        fun `two results should be generated`() {
            assertThat(combinations).hasSize(2)
        }

        @Test
        fun `one result should have only the required types with order preserved`() {
            assertThat(combinations).contains(listOf(NumberPattern), listOf(DateTimePattern))
        }

        @Test
        fun `the other result should have all the types with order preserved`() {
            assertThat(combinations).contains(listOf(StringPattern, NumberPattern, BooleanPattern, DateTimePattern))
        }
    }
}
