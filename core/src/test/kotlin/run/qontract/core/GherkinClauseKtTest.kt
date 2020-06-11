package run.qontract.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.GherkinSection.Given
import run.qontract.core.pattern.DeferredPattern
import run.qontract.core.pattern.TabularPattern
import run.qontract.core.value.ExampleDeclaration

internal class GherkinClauseKtTest {
    @Test
    fun `pipes in tabular pattern is escaped`() {
        val clause = toClause("Data", TabularPattern(mapOf("key|" to DeferredPattern("(Type|)"))))
        println(clause.content)
        assertThat(clause).isEqualTo(GherkinClause("type Data\n  | key\\| | (Type\\|) |", Given))
    }

    @Test
    fun `pipes in deferred pattern is escaped`() {
        val clause = toClause("Data", TabularPattern(mapOf("key|" to DeferredPattern("(Type|)"))))
        println(clause.content)
        assertThat(clause).isEqualTo(GherkinClause("type Data\n  | key\\| | (Type\\|) |", Given))
    }

    @Test
    fun `pipes in examples keys and values are escaped`() {
        val examples = ExampleDeclaration(mapOf("key|" to "value|"))
        val examplesString = toExampleGherkinString(examples)
        assertThat(examplesString).isEqualTo("Examples:\n| key\\| |\n| value\\| |")
    }
}
