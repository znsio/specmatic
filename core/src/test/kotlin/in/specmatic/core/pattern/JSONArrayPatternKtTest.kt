package `in`.specmatic.core.pattern

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import `in`.specmatic.core.Resolver

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

    @Test
    fun allOrNothingTest() {
        val combinations = allOrNothingListCombinations(listOf(listOf(StringPattern, null), listOf(NumberPattern)))
        assertThat(combinations[0]).isEqualTo(listOf(StringPattern, NumberPattern))
        assertThat(combinations[1]).isEqualTo(listOf(NumberPattern))
        println(combinations)
    }
}