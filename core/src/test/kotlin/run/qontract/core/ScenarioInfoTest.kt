package run.qontract.core

import run.qontract.core.pattern.PatternTable
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test

internal class ScenarioInfoTest {
    @Test
    fun `When a ScenarioInfo is deep-copied, the patterns in the new object do not get added to the old one` () {
        val original = ScenarioInfo("test", HttpRequestPattern(), HttpResponsePattern(), HashMap(), HashMap(), HashMap(), mutableListOf())
        val copied = original.deepCopy()

        copied.examples.add(PatternTable())

        assertThat(original.examples).isEmpty()
        assertThat(copied.examples).hasSize(1)
    }
}